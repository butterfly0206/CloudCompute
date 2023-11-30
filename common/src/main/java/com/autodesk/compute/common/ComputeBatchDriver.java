package com.autodesk.compute.common;

import com.amazonaws.services.batch.AWSBatch;
import com.amazonaws.services.batch.AWSBatchClientBuilder;
import com.amazonaws.services.batch.model.*;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.common.ComputeStringOps.makeString;


@Slf4j
public class ComputeBatchDriver {

    private static Set<String> batchTerminalStates = ImmutableSet.<String>builder().add("SUCCEEDED").add("FAILED").build();
    private static final String BATCH_JOB_ID = "Batch job id";

    public enum ForceTerminationFeature {
        ENABLED,
        DISABLED
    };

    private AWSBatch batchClient;
    private ForceTerminationFeature terminationSetting;

    public ComputeBatchDriver(ForceTerminationFeature terminationSetting) {
        this(makeStandardClient(AWSBatchClientBuilder.standard()), terminationSetting);
    }

    public ComputeBatchDriver(AWSBatch batchClient, ForceTerminationFeature terminationSetting) {
        this.batchClient = batchClient;
        this.terminationSetting = terminationSetting;
    }

    public boolean verifyBatchJobTerminated(String batchId, String reason) {
        DescribeJobsRequest describeJobsRequest = new DescribeJobsRequest().withJobs(batchId);
        try (MDCLoader loader = MDCLoader.forField(MDCFields.BATCH_ID, batchId)) {
            DescribeJobsResult result = batchClient.describeJobs(describeJobsRequest);
            if (result.getJobs() == null || result.getJobs().isEmpty()) {
                log.warn(makeString("verifyBatchJobTerminated: No job ", batchId, " was found"));
                return false;
            }
            JobDetail foundJob = result.getJobs().get(0);
            loader.withField(MDCFields.JOB_ID, foundJob.getJobId());
            if (!batchTerminalStates.contains(foundJob.getStatus())) {
                if (terminationSetting == ForceTerminationFeature.ENABLED) {
                    log.warn(makeString(BATCH_JOB_ID, batchId, " was not in terminal state when verifyBatchJobTerminated was called. Killing it."));
                    return forceTerminateBatchJob(batchId, reason);
                } else {
                    log.warn(makeString(BATCH_JOB_ID, batchId, " was not in terminal state when verifyBatchJobTerminated was called. The termination feature is disabled, so no action will be taken."));
                }
            }
            log.info(makeString(BATCH_JOB_ID, batchId, " was completed with status ", foundJob.getStatus()));
            return true;
        } catch (AWSBatchException batchException) {
            log.error(makeString("Failed to call describeJobs for batch job id ", batchId), batchException);
            return false;
        }
    }

    public boolean forceTerminateBatchJob(String batchId, String reason) {
        if (Strings.isNullOrEmpty(batchId)) {
            log.info("forceTerminateBatchJob called with null or empty string; returning false");
            return false;
        }
        if (Strings.isNullOrEmpty(reason))
            reason = "Terminated by CloudOS Compute";
        try (MDCLoader loader = MDCLoader.forField(MDCFields.BATCH_ID, batchId)) {
            TerminateJobRequest request = new TerminateJobRequest().withJobId(batchId).withReason(reason);
            batchClient.terminateJob(request);
            log.warn(makeString(BATCH_JOB_ID, batchId, " was successfully force-terminated by CloudOS Compute"));
            return true;
        } catch (AWSBatchException batchException) {
            log.error(makeString("Failed to call terminateJob for batch job id ", batchId), batchException);
            return false;
        }
    }
}