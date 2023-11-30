package com.autodesk.compute.workermanager.api.impl;

import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.Progress;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobCache;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.util.AuthCheckHelper;
import com.autodesk.compute.workermanager.api.ProgressApiService;
import com.autodesk.compute.workermanager.util.HeartbeatHandler;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.cache.LoadingCache;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Slf4j
@RequestScoped
@Default
public class ProgressApiServiceImpl implements ProgressApiService {

    private final DynamoDBJobClient dynamoDBJobClient;
    private final HeartbeatHandler handler;
    private final Metrics metrics;
    private final LoadingCache<String, JobDBRecord> jobCache;

    @Inject
    public ProgressApiServiceImpl() {
        this(new DynamoDBJobClient(ComputeConfig.getInstance()),
                new HeartbeatHandler(Response.Status.REQUEST_TIMEOUT),
                Metrics.getInstance(),
                JobCache.getInstance().getJobCache());
    }

    public ProgressApiServiceImpl(final DynamoDBJobClient dynamoDBJobClient, final HeartbeatHandler handler, final Metrics metrics, final LoadingCache<String, JobDBRecord> jobCache) {
        this.dynamoDBJobClient = dynamoDBJobClient;
        this.handler = handler;
        this.metrics = metrics;
        this.jobCache = jobCache;
    }

    @Override
    public Response postProgress(final Progress progress, final SecurityContext securityContext)
            throws WorkerManagerException {
        try (final MDCLoader loader = MDCLoader.forField(MDCFields.JOB_ID, progress.getJobID())) {
            loader.withField(MDCFields.OPERATION, "postProgress");
            // getPercent throws if percent is null, so the progress.getPercent() that used to be here is dead code.
            log.info("progressPost: Progress: {}", redactedProgress(progress));


            try {
                //check for impersonation
                final JobDBRecord jobDBRecord = jobCache.get(progress.getJobID()); // does not require current record
                if (jobDBRecord == null) {
                    throw new ComputeException(ComputeErrorCodes.NOT_FOUND, makeString("Job ID ", progress.getJobID(), " not found"));
                }

                final String moniker = jobDBRecord.getService();
                loader.withMoniker(moniker);
                loader.withField(MDCFields.WORKER, jobDBRecord.getWorker());
                loader.withField(MDCFields.PORTFOLIO_VERSION, jobDBRecord.getPortfolioVersion());

                if (!AuthCheckHelper.isServiceAuthorized(moniker, securityContext)) {
                    throw new WorkerManagerException(ComputeErrorCodes.NOT_AUTHORIZED, makeString("This service is not authorized to make progress changes for the  Jobid"));
                }

                // details, if not specified, is filled with an empty object
                final Object details = progress.getDetails();
                final String detailsStr = parseDetailsOrDefault(details, "{}");
                metrics.reportProgressSent();
                dynamoDBJobClient.updateProgress(progress.getJobID(), progress.getPercent(), detailsStr);

                if (!dynamoDBJobClient.isBatchJob(progress.getJobID())) {
                    // Post heartbeat to keep Step activity going and it will also let worker know
                    // if activity has timed out and there is not point on making further progress
                    return handler.processHeartbeat(jobDBRecord, progress.getJobSecret(), securityContext);
                } else {
                    return Response.ok().build();
                }
            } catch (final ComputeException e) {
                if (ThrottleCheck.isThrottlingException(e)) {
                    throw new WorkerManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, e);
                }
                if (e.getCode() == ComputeErrorCodes.JOB_UPDATE_FORBIDDEN) {
                    throw new WorkerManagerException(e.getCode(), "Updating job progress", e);
                }
                throw new WorkerManagerException(ComputeErrorCodes.JOB_PROGRESS_UPDATE_ERROR, e);
            } catch (final ExecutionException e) {
                if (e.getCause() instanceof ComputeException) {
                    final ComputeException computeException = (ComputeException) e.getCause();
                    throw new WorkerManagerException(computeException);
                } else {
                    throw new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, e.getCause());
                }
            }
        }
    }

    private String parseDetailsOrDefault(final Object details, final String defaultValue) {
        String detailsStr = defaultValue;
        if (details != null) {
            try {
                detailsStr = Json.mapper.writeValueAsString(details);
            } catch (final JsonProcessingException e) {
                log.error("Failed to parse progress details", e);
            }
        }
        return detailsStr;
    }

    private static Progress redactedProgress(final Progress progress) {
        final Progress redacted = new Progress();
        redacted.setJobID(progress.getJobID());
        redacted.setPercent(progress.getPercent());
        redacted.setDetails(progress.getDetails());
        redacted.setJobSecret("redacted");
        return redacted;
    }

}
