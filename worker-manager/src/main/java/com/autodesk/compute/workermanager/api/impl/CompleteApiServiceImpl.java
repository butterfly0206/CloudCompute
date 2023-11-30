package com.autodesk.compute.workermanager.api.impl;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;
import com.amazonaws.services.stepfunctions.model.*;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.Conclusion;
import com.autodesk.compute.common.model.Failure;
import com.autodesk.compute.common.model.Status;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobCache;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.util.AuthCheckHelper;
import com.autodesk.compute.workermanager.api.CompleteApiService;
import com.autodesk.compute.workermanager.util.TaskTokenUtils;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.google.common.base.MoreObjects.firstNonNull;

@Slf4j
@RequestScoped
@Default
public class CompleteApiServiceImpl implements CompleteApiService {

    private final LoadingCache<String, JobDBRecord> jobCache;
    private final AWSStepFunctions sfn;
    private final DynamoDBJobClient dynamoDBJobClient;
    private final Metrics metrics;
    private final List<Status> validCompleteStatus = Lists.newArrayList(
            Status.COMPLETED, Status.FAILED);

    @Inject
    public CompleteApiServiceImpl() {
        this(makeStandardClient(AWSStepFunctionsClientBuilder.standard()),
                new DynamoDBJobClient(),
                Metrics.getInstance(),
                JobCache.getInstance().getJobCache());
    }

    public CompleteApiServiceImpl(final AWSStepFunctions sfn, final DynamoDBJobClient dynamoDBJobClient, final Metrics metrics, final LoadingCache<String, JobDBRecord> jobCache) {
        this.sfn = sfn;
        this.dynamoDBJobClient = dynamoDBJobClient;
        this.metrics = metrics;
        this.jobCache = jobCache;
    }

    private void completeSFNActivity(final Conclusion conclusion) throws WorkerManagerException {
        try {
            final Status status = conclusion.getStatus();
            final String taskToken = TaskTokenUtils.getTaskTokenFromJobSecret(conclusion.getJobSecret());
            //complete SFN activity
            if (JobStatus.isCompleted(status)) {
                sendTaskSuccess(taskToken);
            } else {
                sendTaskFailure(taskToken);
            }
        } catch (final ComputeException e) {
            throw new WorkerManagerException(e);
        } catch (final InvalidOutputException e) {
            throw new WorkerManagerException(ComputeErrorCodes.INVALID_JOB_RESULT, e);
        } catch (final TaskDoesNotExistException | InvalidTokenException e) {
            throw new WorkerManagerException(ComputeErrorCodes.NOT_FOUND, e);
        } catch (final TaskTimedOutException e) {
            throw new WorkerManagerException(ComputeErrorCodes.JOB_TIMED_OUT, e);
        } catch (final Exception e) {
            // Check specifically for throttling exception types, wrap if so, rethrow otherwise
            if (ThrottleCheck.isThrottlingException(e)) {
                throw new WorkerManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, e);
            }
            throw new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, e);
        }
    }

    // Job failure is stored in DynamoDB during completion call, no need to store it in activity failure.
    private void sendTaskFailure(final String taskToken) {
        final SendTaskFailureRequest sendTaskFailureRequest = new SendTaskFailureRequest()
                .withError("{}")
                .withCause("{}")
                .withTaskToken(taskToken);
        sfn.sendTaskFailure(sendTaskFailureRequest);
    }

    // Job result is stored in DynamoDB during completion call, no need to store it in activity success.
    private void sendTaskSuccess(final String taskToken) {
        final SendTaskSuccessRequest sendTaskSuccessRequest = new SendTaskSuccessRequest()
                .withTaskToken(taskToken).withOutput("{}");
        sfn.sendTaskSuccess(sendTaskSuccessRequest);
    }

    @SuppressWarnings("squid:CommentedOutCodeLine")
    // The GET /jobs/{id} API expects the following structure for the error data that is stored in dynamodb:
    // It expects an array of strings, each of which looks like the following:
    // TODO: Error records in dynamodb deserve a review of their structure.
    // {
    //     "error": "A string with the error",
    //     "details": { <some valid json object here> }
    // }
    private void storeJobConclusion(final Conclusion conclusion, final JobDBRecord jobRecord) throws WorkerManagerException {
        try {
            final JobStatus jobStatus = JobStatus.isCompleted(conclusion.getStatus()) ?
                    JobStatus.COMPLETED : JobStatus.FAILED;

            if (jobStatus == JobStatus.COMPLETED) {
                final String result = Json.mapper.writeValueAsString(firstNonNull(conclusion.getResult(), "{}"));
                dynamoDBJobClient.markJobCompleted(conclusion.getJobID(), null, result, jobRecord.getUserServiceWorkerId());
            } else {
                @SuppressWarnings("squid:CommentedOutCodeLine")
                // This is what it looks like: "{ "error": "some message", "details": { a-json-object } }"
                String err = conclusion.getError();
                final Failure failure = new Failure();
                if (ComputeStringOps.isNullOrEmpty(err))
                    err = "No error description reported";
                failure.setError(err);
                if (conclusion.getDetails() != null)
                    failure.setDetails(conclusion.getDetails());
                dynamoDBJobClient.markJobFailed(conclusion.getJobID(), jobRecord.getIdempotencyId(),  Arrays.asList(failure), null, jobRecord.getUserServiceWorkerId());
            }
        } catch (final JsonProcessingException e) {
            throw new WorkerManagerException(ComputeErrorCodes.INVALID_JOB_RESULT, e);
        } catch (final ComputeException e) {
            throw new WorkerManagerException(e);
        } catch (final Exception e) {
            throw new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, e);
        }
    }

    @Override
    public Response postComplete(final Conclusion conclusion, final SecurityContext securityContext)
            throws WorkerManagerException {
        try (final MDCLoader loader = MDCLoader.forField(MDCFields.JOB_ID, conclusion.getJobID())) {
            loader.withField(MDCFields.OPERATION, "postComplete");
            Optional.ofNullable(conclusion.getStatus())
                    .filter(validCompleteStatus::contains)
                    .orElseThrow(() -> new WorkerManagerException(ComputeErrorCodes.BAD_REQUEST, // NOSONAR
                            makeString("Conclusion.Status must be ", validCompleteStatus, "not", conclusion.getStatus())));
            try {
                final JobDBRecord currentJob = jobCache.get(conclusion.getJobID()); // does not require current record
                if (currentJob == null) {
                    throw new ComputeException(ComputeErrorCodes.NOT_FOUND, makeString("Job ID ", conclusion.getJobID(), " not found"));
                }
                loader.withMoniker(currentJob.getService());
                loader.withField(MDCFields.WORKER, currentJob.getWorker());
                loader.withField(MDCFields.PORTFOLIO_VERSION, currentJob.getPortfolioVersion());
                final long jobFinishedTimeSeconds = Instant.now().getEpochSecond();

                if (!AuthCheckHelper.isServiceAuthorized(currentJob.getService(), securityContext)) {
                    throw new WorkerManagerException(ComputeErrorCodes.NOT_AUTHORIZED, makeString("This service is not authorized to post complete for the  Job {}", currentJob.getJobId()));
                }

                if (!currentJob.isBatch()) {
                    completeSFNActivity(conclusion);
                }
                storeJobConclusion(conclusion, currentJob);

                if (JobStatus.isCompleted(conclusion.getStatus())) {
                    metrics.reportJobSucceeded(currentJob.getService(), currentJob.getWorker());
                } else {
                    metrics.reportJobFailed(currentJob.getService(), currentJob.getWorker());
                }

                // Jobs must last at least 0 seconds - deal with potential clock slew
                final long jobDuration = Math.max(jobFinishedTimeSeconds - currentJob.getCreationTimeMillis() / 1_000, 0);
                metrics.reportJobDuration(currentJob.getService(), currentJob.getWorker(),
                        (double) jobDuration);
                log.info("postComplete: {}", conclusion);

                return Response.ok().build();
            } catch (final ComputeException e) {
                throw new WorkerManagerException(e);
            } catch (final WorkerManagerException e) {
                if (e.getCode() == ComputeErrorCodes.JOB_TIMED_OUT) {
                    return Response.status(Response.Status.REQUEST_TIMEOUT).build();
                }
                throw e;
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
}
