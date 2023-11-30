package com.autodesk.compute.jobmanager.util;

import com.amazonaws.util.StringUtils;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.model.*;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.discovery.ServiceResources;
import com.autodesk.compute.dynamodb.*;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.model.dynamodb.InsertDBRecordArgs;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.model.dynamodb.SearchJobsResult;
import com.autodesk.compute.util.JsonHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.LoadingCache;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Slf4j
public class JobStoreHelper {
    private final Metrics metrics;
    private final LoadingCache<String, JobDBRecord> jobCache;
    private final DynamoDBJobClient dynamoDbJobClient;
    private final DynamoDBIdempotentJobsClient idempotentJobsClient;
    private final DynamoDBActiveJobsCountClient activeJobsCountClient;

    @Inject
    public JobStoreHelper() {
        this(new DynamoDBJobClient(
                        ComputeConfig.getInstance()),
                new DynamoDBIdempotentJobsClient(),
                new DynamoDBActiveJobsCountClient(),
                Metrics.getInstance(),
                JobCache.getInstance().getJobCache());
    }

    public JobStoreHelper(final DynamoDBJobClient dynamoDbJobClient,
                          final DynamoDBIdempotentJobsClient idempotentJobsClient,
                          final DynamoDBActiveJobsCountClient activeJobsCountClient,
                          final Metrics metrics,
                          final LoadingCache<String, JobDBRecord> jobCache) {
        this.dynamoDbJobClient = dynamoDbJobClient;
        this.idempotentJobsClient = idempotentJobsClient;
        this.activeJobsCountClient = activeJobsCountClient;
        this.metrics = metrics;
        this.jobCache = jobCache;
    }

    public JobDBRecord insertDBRecord(@NonNull final JobArgs args, @NonNull final String jobId, final String oxygenToken, final String serviceClientId,
                                      final ServiceResources.WorkerResources workerResources, final String userId, final String userType, final boolean isBatch, final String queueType) throws JobManagerException {

        return insertDBRecord(
                new InsertDBRecordArgs(args.getService(), args.getWorker(), jobId, oxygenToken, serviceClientId, workerResources, userId, userType, isBatch, args.getTags(), args.getPayload(), args.getIdempotencyId(), queueType));
    }

    public List<JobDBRecord> insertDBRecords(@NonNull final ArrayJobArgs jobArgs, @NonNull final String arrayJobId, final String oxygenToken, final String serviceClientId,
                                             final ServiceResources.WorkerResources workerResources, final String userId, final String userType, final boolean isBatch, final String queueType) throws JobManagerException {

        final List<JobDBRecord> jobDBRecords = new ArrayList<>();
        for (int i = 0; i < jobArgs.getJobs().size(); i++) {
            // Each job in array  is assigned Id from "arrayJobId:0" -> "arrayJobId:N"
            final String jobId = makeString(arrayJobId, ":", i);
            final JobDBRecord record;
            try {
                record = new InsertDBRecordArgs(jobArgs.getService(), jobArgs.getWorker(), jobId, oxygenToken, serviceClientId,
                        workerResources, userId, userType, isBatch, jobArgs.getJobs().get(i).getTags(),
                        jobArgs.getJobs().get(i).getPayload(), null, queueType).toJobDBRecord();
                jobDBRecords.add(record);
            } catch (final JsonProcessingException e) {
                throw new JobManagerException(ComputeErrorCodes.BAD_REQUEST, e);
            }
        }
        try {
            return dynamoDbJobClient.insertJobs(jobDBRecords);
        } catch (final ComputeException e) {
            throw new JobManagerException(e);
        }
    }


    private JobDBRecord insertDBRecord(final InsertDBRecordArgs insertDBRecordArgs) throws JobManagerException {
        try {
            return dynamoDbJobClient.insertJob(insertDBRecordArgs.toJobDBRecord());
        } catch (final ComputeException e) {
            throw new JobManagerException(e);
        } catch (final JsonProcessingException e) {
            throw new JobManagerException(ComputeErrorCodes.BAD_REQUEST, e);
        }
    }

    public void validateIdempotency(final JobArgs jobArgs) throws JobManagerException {
        try {
            if (!StringUtils.isNullOrEmpty(jobArgs.getIdempotencyId())
                    && idempotentJobsClient.getJob(jobArgs.getIdempotencyId()) != null) { // does not require current record
                final String msg = makeString("Job with idempotencyId: ", jobArgs.getIdempotencyId(), " already exists.");
                throw new JobManagerException(ComputeErrorCodes.IDEMPOTENT_JOB_EXISTS, msg);
            }
        } catch (final ComputeException e) {
            final String error = makeString("Failed to add job with idempotencyId: ", jobArgs.getIdempotencyId());
            log.error(error, e);
            throw new JobManagerException(e);
        }
    }

    public int getActiveJobsCount(final String queueId) throws ComputeException {
        return activeJobsCountClient.getCount(queueId);
    }

    public JobDBRecord peekJob(final String queueId) {
        return dynamoDbJobClient.peekJob(queueId);
    }

    public void removeJobFromQueue(final JobDBRecord aJobFromQueue) throws ComputeException {
        dynamoDbJobClient.removeJobFromQueue(aJobFromQueue);
    }

    public void markJobScheduled(final JobDBRecord dbRecord) throws ComputeException {
        dynamoDbJobClient.markJobScheduled(dbRecord.getJobId(), dbRecord.getUserServiceWorkerId());
    }

    public void markJobCanceled(final JobDBRecord dbRecord) throws ComputeException {
        dynamoDbJobClient.markJobCanceled(dbRecord);
    }

    public void markJobFailed(final String jobId, final String idempotencyId, final List<Failure> failures, final String output, final String userServiceWorker) throws ComputeException {
        dynamoDbJobClient.markJobFailed(jobId, idempotencyId, failures, null, null);
    }

    public void addStepExecutionArn(final JobDBRecord dbRecord, final String stepExecutionArn) throws ComputeException {
        dynamoDbJobClient.addStepExecutionArn(dbRecord.getJobId(), stepExecutionArn);
    }

    public void setBatchJobId(final String jobId, final String spawnedBatchJobId) throws ComputeException {
        dynamoDbJobClient.setBatchJobId(jobId, spawnedBatchJobId);
    }

    public void safelyRemoveIfQueued(final JobDBRecord dbRecord) {
        if (JobStatus.QUEUED.toString().equals(dbRecord.getStatus())) {
            try {
                dynamoDbJobClient.removeJobFromQueue(dbRecord);
            } catch (final ComputeException e) {
                if (e.getCode() == ComputeErrorCodes.JOB_UPDATE_FORBIDDEN) {
                    // Someone may have already removed it from queue since our last check which is fine
                    log.info("deleteJob: fail to remove job from queue, JobId: {}", dbRecord.getJobId(), e);
                } else {
                    log.error("deleteJob: fail to remove job from queue, JobId: {}", dbRecord.getJobId(), e);
                }
            }
        }
    }

    public JobDBRecord getJobFromCache(final String id, final boolean refresh) throws JobManagerException {
        if (!JobDBRecord.isValidJobId(id) || JobDBRecord.isValidArrayJobId(id))
            return null;

        // get job or exception
        final JobDBRecord job;
        try {
            final Instant callStart = Instant.now();
            if (refresh) {
                jobCache.refresh(id);
            }
            job = jobCache.get(id);
            final Duration durationGetJobFromCache = Duration.between(callStart, Instant.now());
            metrics.reportGetJobFromCacheDuration(durationGetJobFromCache.toMillis() / 1_000.0);
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof ComputeException) {
                final ComputeException computeException = (ComputeException) e.getCause();
                if (computeException.getCode() == ComputeErrorCodes.NOT_FOUND)
                    return null;
                throw new JobManagerException(computeException.getCode(), computeException);
            } else {
                throw new JobManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, makeString("Job ", id, " not found"), e.getCause());
            }
        }
        return job;
    }

    public ArrayJobResult getArrayJob(final String id, final String nextToken) throws JobManagerException {

        if (!JobDBRecord.isValidArrayJobId(id))
            return null;

        // get job or exception
        try {
            final SearchJobsResult result = dynamoDbJobClient.getArrayJobs(id, nextToken);
            final ArrayJobResult arrayJob = new ArrayJobResult();
            // no jobs found
            if (result == null || result.getJobs() == null || result.getJobs().size() == 0) {
                return null;
            }
            arrayJob.setJobID(result.getJobs().get(0).getArrayJobId());
            arrayJob.setService(result.getJobs().get(0).getService());
            arrayJob.setWorker(result.getJobs().get(0).getWorker());
            arrayJob.setPortfolioVersion(result.getJobs().get(0).getResolvedPortfolioVersion());
            final List<JobInfo> jobs = new ArrayList<>();
            for (final JobDBRecord jobRecord : result.getJobs()) {
                jobs.add(JsonHelper.convertToJobInfo(jobRecord));
            }
            arrayJob.setJobs(jobs);
            arrayJob.setNextToken(result.getNextToken());
            return arrayJob;
        } catch (final ComputeException e) {
            if (e.getCode() == ComputeErrorCodes.NOT_FOUND)
                return null;
            throw new JobManagerException(e.getCode(), e);
        }
    }

    public SearchJobsResult searchArrayJob(final String id, final String nextToken) throws ComputeException {
        if (!JobDBRecord.isValidArrayJobId(id))
            return null;
        return dynamoDbJobClient.getArrayJobs(id, nextToken);
    }

    public JsonNode getErrorDetailsForJob(final Job job) {
        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode emptyNode = objectMapper.createObjectNode();
        return getErrorDetailsForJob(job, emptyNode);
    }

    public JsonNode getErrorDetailsForJob(final Job job, final JsonNode origDetails) {
        // Build a json dictionary containing job details
        final ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode details = objectMapper.createObjectNode();
        details.put(MDCFields.JOB_ID, job.getJobID());
        details.put(MDCFields.SERVICE, job.getService());
        details.put(MDCFields.WORKER, job.getWorker());
        details.put("portfolioVersion", job.getPortfolioVersion());
        details.put("status", job.getStatus().toString());

        if (origDetails != null) {
            final Iterator<Map.Entry<String, JsonNode>> it = origDetails.fields();
            // https://www.techiedelight.com/convert-iterator-iterable-java/
            final Iterable<Map.Entry<String, JsonNode>> iterable = () -> it;
            for (final Map.Entry<String, JsonNode> item : iterable) {
                details.set(item.getKey(), item.getValue());
            }
        }
        return details;
    }

    public void updateJobTags(final String jobId, final List<String> tags) throws JobManagerException {
        try {
            dynamoDbJobClient.updateTags(jobId, tags);
            jobCache.invalidate(jobId);
        } catch (final ComputeException e) {
            if (e.getCode() == ComputeErrorCodes.JOB_UPDATE_FORBIDDEN) {
                throw new JobManagerException(e.getCode(), "Updating job tag", e);
            }
            throw new JobManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, e);
        }
    }

}
