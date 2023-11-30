package com.autodesk.compute.workermanager.api.impl;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;
import com.amazonaws.services.stepfunctions.model.*;
import com.autodesk.compute.common.CloudWatchClient;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.PollResponse;
import com.autodesk.compute.common.model.Status;
import com.autodesk.compute.common.model.StatusUpdate;
import com.autodesk.compute.common.model.Worker;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.discovery.ServiceResources;
import com.autodesk.compute.discovery.WorkerResolver;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.util.AuthCheckHelper;
import com.autodesk.compute.util.JsonHelper;
import com.autodesk.compute.workermanager.api.PollApiService;
import com.autodesk.compute.workermanager.util.TaskTokenUtils;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.pivovarit.function.ThrowingFunction;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.common.ServiceWithVersion.isCurrentVersion;
import static com.autodesk.compute.common.ServiceWithVersion.makeKey;
import static com.google.common.base.MoreObjects.firstNonNull;

@Slf4j
@RequestScoped
@Default
public class PollApiServiceImpl implements PollApiService {

    public static final String POLL_FOR_JOB = "pollForJob";
    private static final String REQUESTED_JOB_NOT_FOUND_PLEASE_TRY_AGAIN = "Requested job not found. Please try again.";
    private static final String REQUESTED_ACTIVITY_NOT_FOUND = "Step activity or task not found for worker.";
    private static final String ACTIVITY_INPUT_MISSING_JOBID = "Step activity input does not contain a jobId.";

    private final AWSStepFunctions sfn;
    private final Metrics metrics;
    private final DynamoDBJobClient dynamoDBJobClient;
    private final CloudWatchClient cloudWatchClient;


    // Needed for @Inject to work
    @Inject
    public PollApiServiceImpl() {
        // Do not use makeStandardClient with the Stepfunctions builder! The standard client retries when it gets errors
        // but that's the normal case for the StepFunction getActivityTask API.
        this(AWSStepFunctionsClientBuilder.defaultClient(),
                Metrics.getInstance(),
                new DynamoDBJobClient(ComputeConfig.getInstance()),
                new CloudWatchClient());
    }

    public PollApiServiceImpl(final AWSStepFunctions sfn, final Metrics metrics, final DynamoDBJobClient dynamoDBJobClient, final CloudWatchClient cloudWatchClient) {
        this.sfn = sfn;
        this.metrics = metrics;
        this.dynamoDBJobClient = dynamoDBJobClient;
        this.cloudWatchClient = cloudWatchClient;
    }

    public ServiceResources.WorkerResources getWorkerResource(final String service, final String worker, final String portfolioVersion, final boolean noBatch) throws WorkerManagerException {
        Optional<ServiceResources.WorkerResources> testWorkerResources = Optional.empty();
        Optional<ServiceResources.WorkerResources> deployedWorkerResources = Optional.empty();
        Optional<ServiceResources.WorkerResources> resolvedWorkerResources = Optional.empty();
        try {
            // Figure out if the worker making the API call is the currently deployed version.
            // If it is, then give it a current version job. Otherwise give it a test job.
            // An exception to that rule: if noBatch = true, then we attempt to give it a test worker
            // only, while validating that it must be a batch worker's configuration.
            boolean currentlyDeployedWorker = false;
            if (isCurrentVersion(portfolioVersion)) {
                deployedWorkerResources = WorkerResolver.getInstance().getWorkerResources(
                        service, worker, portfolioVersion);
                currentlyDeployedWorker = true;
            } else {
                testWorkerResources = WorkerResolver.getInstance().getWorkerResources(
                        service, worker, portfolioVersion);
                deployedWorkerResources = WorkerResolver.getInstance().getWorkerResources(
                        service, worker, null);
                currentlyDeployedWorker = deployedWorkerResources.map(
                        workerResources -> (workerResources.getPortfolioVersion() != null && workerResources.getPortfolioVersion().equals(portfolioVersion))).orElse(false) && !noBatch;
            }

            resolvedWorkerResources = currentlyDeployedWorker ? deployedWorkerResources : testWorkerResources;

            if (!resolvedWorkerResources.isPresent()) {
                if (!WorkerResolver.getInstance().hasService(service)) {
                    throw new WorkerManagerException(ComputeErrorCodes.INVALID_JOB_PAYLOAD,
                            String.format("Unknown service '%s' in query", service));
                } else if (!WorkerResolver.getInstance().hasService(makeKey(service, portfolioVersion))) {
                    throw new WorkerManagerException(ComputeErrorCodes.INVALID_JOB_PAYLOAD,
                            String.format("Unknown portfolioVersion '%s' for service '%s' in query", portfolioVersion, service));
                } else {
                    throw new WorkerManagerException(ComputeErrorCodes.INVALID_JOB_PAYLOAD,
                            String.format("Unknown worker '%s' for service '%s' and portfolioVersion '%s' in query", worker, service, portfolioVersion));
                }
            } else {
                // One other case to verify: noBatch is *only* allowed for batch workers.
                final ServiceResources.WorkerResources resource = resolvedWorkerResources.get();
                final boolean isBatch = resource instanceof ServiceResources.BatchWorkerResources;
                if (noBatch && !isBatch) {
                    log.error("NoBatch is only allowed for batch workers -- moniker {} with worker {} needs to fix how it polls for jobs", service, worker);
                }
            }
        } catch (final ComputeException e) {
            throw new WorkerManagerException(e);
        }
        return resolvedWorkerResources.get();
    }

    private GetActivityTaskResult redact(final GetActivityTaskResult result) {
        return result.clone().withTaskToken("redacted");
    }

    // TODO: refactor this method so exceptions are exceptional and not the common case
    private GetActivityTaskResult pollForActivity(final Worker pollData, final ServiceResources.WorkerResources workerResources) throws WorkerManagerException {
        //poll for SFN activity
        final GetActivityTaskRequest getActivityTaskRequest = new GetActivityTaskRequest()
                .withActivityArn(workerResources.getActivityArn())
                .withWorkerName(workerResources.getWorkerIdentifier());

        try {
            log.info("Requesting an activity for worker {}@{}@{}", pollData.getService(), pollData.getWorker(), pollData.getPortfolioVersion());
            final GetActivityTaskResult getActivityTaskResult = sfn.getActivityTask(getActivityTaskRequest);
            log.info("Got a response of an activity for worker {}@{}@{}", pollData.getService(), pollData.getWorker(), pollData.getPortfolioVersion());
            if (ComputeStringOps.isNullOrEmpty(getActivityTaskResult.getTaskToken())) {
                throw new WorkerManagerException(ComputeErrorCodes.JOB_NOT_FOUND, REQUESTED_JOB_NOT_FOUND_PLEASE_TRY_AGAIN);
            }
            log.info("GetActivityTaskResult: {}", redact(getActivityTaskResult));
            return getActivityTaskResult;
        } catch (final ActivityWorkerLimitExceededException e) {
            //do we need a special error for rate limiting?
            throw new WorkerManagerException(ComputeErrorCodes.SERVER_BUSY, e);
        } catch (final ActivityDoesNotExistException | InvalidArnException e) {
            log.error("pollForActivity: Failed to get activity", e);
            throw new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, e);
        } catch (final SdkClientException timeoutException) {
            // This is a little deep to know, but it comes from the code. SocketTimeoutException results in SdkClientException.
            if (timeoutException.getCause() instanceof SocketTimeoutException) {
                throw new WorkerManagerException(ComputeErrorCodes.JOB_NOT_FOUND, REQUESTED_JOB_NOT_FOUND_PLEASE_TRY_AGAIN);
            } else {
                throw new WorkerManagerException(ComputeErrorCodes.BAD_REQUEST, timeoutException);
            }
        } catch (final Exception e) {
            // Check specifically for throttling exception types, wrap if so, rethrow otherwise
            if (ThrottleCheck.isThrottlingException(e)) {
                throw new WorkerManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, e);
            }
            throw e;
        }

    }

    public Optional<String> getJobIdFromActivityInput(final String activityInput) {
        log.info("Activity input: {}", activityInput);
        // If the activity input cannot be parsed into JSON, we will log the error, but will not throw
        final ThrowingFunction<String, Object, JsonProcessingException> stringToJson = JsonHelper::stringToJson;
        return Optional.ofNullable(activityInput)
                .filter(s -> !ComputeStringOps.isNullOrEmpty(s))
                .flatMap(stringToJson.lift())
                .map(JsonNode.class::cast)
                .map(js -> js.get("jobId"))
                .map(JsonNode::asText)
                .filter(s -> !ComputeStringOps.isNullOrEmpty(s));
    }

    private void validateNonEmptyOrThrow(final String toTest, final String message) throws WorkerManagerException {
        if (ComputeStringOps.isNullOrEmpty(toTest))
            throw new WorkerManagerException(ComputeErrorCodes.BAD_REQUEST, message);
    }

    @Override
    public Response pollForJob(final Worker pollData, final SecurityContext securityContext) throws WorkerManagerException {
        try (final MDCLoader loader = MDCLoader.forField(MDCFields.SERVICE, pollData.getService())) {
            loader.withField(MDCFields.OPERATION, POLL_FOR_JOB);
            loader.withMoniker(firstNonNull(pollData.getService(), MDCFields.UNSPECIFIED));
            loader.withField(MDCFields.WORKER, firstNonNull(pollData.getWorker(), MDCFields.UNSPECIFIED));
            loader.withField("requestedPortfolioVersion", firstNonNull(pollData.getPortfolioVersion(), MDCFields.UNSPECIFIED));

            validateNonEmptyOrThrow(pollData.getService(), "Service must not be null or empty");
            validateNonEmptyOrThrow(pollData.getWorker(), "Worker must not be null or empty");

            /*
             * Checks if the security context provided, is authorized to perform Heartbeat operation on this particular job.
             */

            if (!AuthCheckHelper.isServiceAuthorized(pollData.getService(), securityContext)) {
                throw new WorkerManagerException(ComputeErrorCodes.NOT_AUTHORIZED, makeString("The JOB polling is not authorized for this service"));
            }

            final boolean noBatch = false;    // TODO: support this for service integration workers
            final ServiceResources.WorkerResources workerResources = getWorkerResource(pollData.getService(), pollData.getWorker(), pollData.getPortfolioVersion(), noBatch);
            final boolean workerResourcesIsBatch = workerResources instanceof ServiceResources.BatchWorkerResources;
            if (workerResourcesIsBatch && !noBatch) {
                log.error("Polling batch for {} worker {} - only noBatch jobs will be delivered.", pollData.getService(), pollData.getWorker());
            }

            final GetActivityTaskResult getActivityTaskResult = pollForActivity(pollData, workerResources);

            // We expect to see the jobId in the step function input. If it's not there, retrying won't help, so we need to barf w a 500
            final String jobId = getJobIdFromActivityInput(getActivityTaskResult.getInput())
                    .orElseThrow(() -> new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, ACTIVITY_INPUT_MISSING_JOBID));
            // So we get a jobId, but it's null/blank.  Showstopper, so ... 500
            if (ComputeStringOps.isNullOrEmpty(jobId)) {
                throw new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, ACTIVITY_INPUT_MISSING_JOBID);
            }
            loader.withField(MDCFields.JOB_ID, jobId);

            try {
                final JobDBRecord jobRecord = getJobDBRecord(jobId);

                // Since now we have cases where jobs can start in "QUEUED" rather than "SCHEDULED",
                // we need to figure out exactly when was it that the job moved to SCHEDULED state,
                // else, default to (now - jobCreatedTime) as best guess
                final ThrowingFunction<String, JsonNode, JsonProcessingException> stringToJson = JsonHelper::stringToJson;
                final double jobScheduledDurationMillis = jobRecord.getStatusUpdates()
                        .stream()
                        .map(stringToJson.lift())
                        .flatMap(o -> o.isPresent() ? Stream.of(o.get()) : Stream.empty())
                        .map(js -> {
                            final StatusUpdate statusUpdate = new StatusUpdate();
                            statusUpdate.setStatus(Status.valueOf(js.get("status").asText()));
                            statusUpdate.setTimestamp(js.get("timestamp").asText());
                            return statusUpdate;
                        })
                        .filter(s -> Status.SCHEDULED.equals(s.getStatus()))
                        .findAny()
                        .map(StatusUpdate::getTimestamp)
                        .map(Long::valueOf)
                        .map(t -> Instant.now().toEpochMilli() - t)
                        .map(Long::doubleValue)
                        .orElseGet(() -> (double) Instant.now().toEpochMilli() - jobRecord.getCreationTimeMillis());

                // Scheduled duration metrics reported in seconds, not milliseconds.
                metrics.reportJobScheduledDuration(
                        pollData.getService(),
                        pollData.getWorker(),
                        jobScheduledDurationMillis / 1_000.0);

                // Set our payload from database record (NOW removed from Activity args)
                // It should always be JSON; the exception is just here to be on the safe side
                final JsonNode payloadObject = Optional.of(jobRecord.findPayload())
                        .flatMap(stringToJson.lift())
                        .orElseThrow(() -> new ComputeException(ComputeErrorCodes.INVALID_JOB_PAYLOAD, "Payload is not JSON"));

                dynamoDBJobClient.markJobInprogress(jobId);
                CompletableFuture.runAsync(() -> {
                    metrics.reportJobStarted(pollData.getService(), pollData.getWorker());
                    cloudWatchClient.createEvent(jobId, jobRecord.getService(), jobRecord.getWorker(), Status.INPROGRESS.name(), "workerManager");
                });

                final PollResponse pollResponse = new PollResponse();
                pollResponse.setPayload(payloadObject);
                pollResponse.setJobID(jobId);
                pollResponse.setTags(jobRecord.getTags());
                // Log pollResponse with a redacted secret as we don't want the real thing in the logs
                pollResponse.setJobSecret("redacted");
                final String jobSecret = TaskTokenUtils.getJobSecretFromTaskToken(getActivityTaskResult.getTaskToken());
                pollResponse.setJobSecret(jobSecret);
                return Response.ok().entity(pollResponse).build();
            } catch (final ComputeException e) {
                if (ThrottleCheck.isThrottlingException(e)) {
                    throw new WorkerManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, e);
                }
                log.error(makeString("Unexpected error during pollForJob", e.getMessage()), e);
                throw new WorkerManagerException(e);
            } catch (final Exception e) {
                // Check specifically for throttling exception types, wrap if so, rethrow otherwise
                if (ThrottleCheck.isThrottlingException(e)) {
                    throw new WorkerManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, e);
                }
                throw new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, e);
            }
        }
    }

    private JobDBRecord getJobDBRecord(final String jobId) throws ComputeException, WorkerManagerException {
        final JobDBRecord jobRecord = dynamoDBJobClient.getJob(jobId);  // requires current record (status updates for calculating jobScheduledDuration)
        if (jobRecord == null) {
            log.error(makeString("FATAL: job id ", jobId, " was not found in the database in the poll API"));
            throw new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, "Existing job not found in database.");
        }
        return jobRecord;
    }
}
