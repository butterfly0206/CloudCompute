package com.autodesk.compute.completionhandler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.AmazonSNSException;
import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.util.StringUtils;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.model.Failure;
import com.autodesk.compute.common.model.Job;
import com.autodesk.compute.common.model.Status;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.cosv2.ComputeSpecification;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.util.JsonHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;

//Throw exception only when we could not connect to db or update the record. Do not throw for missing records
@Slf4j
public class SFNCompletionHandler implements RequestHandler<Map<String, Object>, String> {
    private final DynamoDBJobClient dynamoDBJobClient;

    @Inject
    public SFNCompletionHandler() {
        this.dynamoDBJobClient = new DynamoDBJobClient(new CompletionHandlerConfig());
    }

    public DynamoDBJobClient getDynamoDBJobClient() {
        return dynamoDBJobClient;
    }

    public AmazonSNS getSNSClient() {
        return makeStandardClient(AmazonSNSClientBuilder.standard());
    }

    // Backward compatibility
    public String Handler(final Map<String, Object> event, final Context context) {
        return handleRequest(event, context);
    }

    // Lambda entry point
    @SneakyThrows
    @Override
    public String handleRequest(final Map<String, Object> event, final Context context) {
        // Refer to https://docs.aws.amazon.com/step-functions/latest/dg/cw-events.html#cw-events-events
        // to see what step function events look like
        if (event.containsKey("Healthcheck")) {
            return "HealthcheckPassed!";
        }
        else if (event.containsKey("source") && "aws.states".equals(event.get("source")) && event.containsKey("detail")) {
            return handleStepEvent(event, context);
        } else {
            return handleStepOutput(event);
        }
    }

    private String handleStepEvent(final Map<String, Object> event, final Context context) throws JsonProcessingException, ComputeException {
        final Map<String, Object> detail = (Map<String, Object>) event.get("detail");
        final SfnStatus sfnStatus = SfnStatus.valueOf((String) detail.get("status"));
        // RUNNING status is not handled by lambda and CloudWatch rule filters it out.
        // check for them and return immediately just in case
        if (sfnStatus == SfnStatus.RUNNING) {
            return sfnStatus.toString();
        }

        final String input = (String) detail.get("input");
        final HashMap<String, Object> inputMap = new ObjectMapper().readValue(input, HashMap.class);

        // When SFN times out or aborted there wont be any output we have to look into input to find out jobId, etc.
        if (sfnStatus == SfnStatus.TIMED_OUT) {
            return handleTimeout(inputMap);
        } else if (sfnStatus == SfnStatus.SUCCEEDED || sfnStatus == SfnStatus.FAILED || sfnStatus == SfnStatus.ABORTED) {
            return handleCompletedFailedCanceled(inputMap, sfnStatus, context);
        } else { // In case SFN introduces new status that we don't handle
            return sfnStatus.toString();
        }
    }

    // We only need to send SNS notification for SUCCEEDED, FAILED and ABORTED as they are already marked in DB
    // by WorkerManager
    public String handleCompletedFailedCanceled(final Map<String, Object> input, final SfnStatus sfnStatus, final Context context) throws ComputeException {
        final LambdaLogger logger = context.getLogger();

        try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "handleCompletedFailedCanceled")) {
            final String jobId = input.get("jobId").toString();

            loader.withField(MDCFields.JOB_ID, jobId);

            final JobDBRecord job = getDynamoDBJobClient().getJob(jobId); // requires current record (exits early if in terminal state)
            log.info(makeString("jobId: ", jobId, " ", job.getStatus()));

            // Due to how secret validation happens for polling jobs, we complete the activity first to validate
            // activity token (which is the job secret) and then mark the job complete. Because of this race condition
            // we may see that job is still in progress while it will eventually be marked completed or failed.
            // So for correctness of sns notification, if we see that job is not completed or failed simply deduce
            // the job status from the state execution event.
            if(!JobStatus.valueOf(job.getStatus()).isTerminalState()) {
                JobStatus expectedStatus = JobStatus.FAILED;
                switch (sfnStatus) {
                    case SUCCEEDED:
                        expectedStatus = JobStatus.COMPLETED;
                        break;
                    case ABORTED:
                        expectedStatus = JobStatus.CANCELED;
                        break;
                }
                log.info(makeString("jobId: ", jobId, " found in ", job.getStatus(),
                        " estimating eventual status to ", expectedStatus.toString(), " for notification purpose."));
                job.setStatus(expectedStatus.toString());

                // CHIMERA-614.  Edge case where step function fails (exhausted retries)
                // may indicate unresponsive worker, so we set the job status to FAILED
                if (sfnStatus == SfnStatus.FAILED) {
                    final Failure failure = new Failure();
                    failure.setError("Step function failed, setting job FAILED");
                    return markJobAndNotify(input, job, null, failure);
                }
            }
            notify(job);
            return job.getStatus();
        }
    }

    public String handleTimeout(final Map<String, Object> input) throws ComputeException {
        try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "handleTimeout")) {
            final String jobId = input.get("jobId").toString();

            loader.withField(MDCFields.JOB_ID, jobId);
            log.info(makeString("jobId: ", jobId, " timed-out."));

            // If worker is using service integration, the results are already in dynamoDB
            // simply return the status
            final JobDBRecord job = getDynamoDBJobClient().getJob(jobId);

            final Failure failure = new Failure();
            failure.setError("Job timed-out.");
            return markJobAndNotify(input, job, null, failure);
        }
    }

    public String handleStepOutput(final Map<String, Object> output) throws ComputeException {
        try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "handleStepOutput")) {
            final String jobId = output.get("jobId").toString();
            loader.withField(MDCFields.JOB_ID, jobId);
            log.info(makeString("jobId: ", jobId));

            final JobDBRecord job = getDynamoDBJobClient().getJob(jobId);

            // See if activity got some error, mostly happens when it times out.
            final Failure failure = extractFailure(jobId, output);
            final JobStatus currentJobStatus = JobStatus.valueOf(job.getStatus());
            if (failure == null || currentJobStatus.isTerminalState()) {
                // the results are already in dynamoDB, simply return the status
                return job.getStatus();
            }
            final List<Failure> failures = ImmutableList.<Failure>builder().add(failure).build();
            getDynamoDBJobClient().markJobFailed(jobId, job.getIdempotencyId(), failures, null, job.getUserServiceWorkerId());
            return JobStatus.FAILED.toString();
        }
    }

    private String markJobAndNotify(final Map<String, Object> output, final JobDBRecord job,
                                    final String result, final Failure failure) throws ComputeException {

        final JobStatus status = failure != null ? JobStatus.FAILED : JobStatus.COMPLETED;

        if (failure == null) {
            getDynamoDBJobClient().markJobCompleted(job.getJobId(), null, result, job.getUserServiceWorkerId());
        } else {
            final List<Failure> failures = ImmutableList.<Failure>builder().add(failure).build();
            getDynamoDBJobClient().markJobFailed(job.getJobId(), job.getIdempotencyId(), failures, result, job.getUserServiceWorkerId());
        }

        notify(output, job, failure, status);
        return status.toString();
    }

    private void notify(final Map<String, Object> output, final JobDBRecord job, final Failure failure, final JobStatus status) {
        if (!StringUtils.isNullOrEmpty(job.getJobStatusNotificationEndpoint())
                && job.getJobStatusNotificationType() != null
                && job.getJobStatusNotificationType().equalsIgnoreCase("sns")) {
            final Optional<Object> nestedOutput = Optional.ofNullable(output.get("output"));
            final Object resultObj = nestedOutput.orElse(new Object());
            sendJobCompletionNotification(job.getWorker(), job, status, resultObj, failure);
        }
    }

    private void notify(final JobDBRecord job) {
        if (!StringUtils.isNullOrEmpty(job.getJobStatusNotificationEndpoint())
                && job.getJobStatusNotificationType() != null
                && job.getJobStatusNotificationType().equalsIgnoreCase("sns")) {
            HashMap<String, Object> jobOutput;
            try {
                jobOutput = new ObjectMapper().readValue(firstNonNull(job.getResult(), "{}"), HashMap.class);
            } catch (final JsonProcessingException e) {
                log.info(makeString("Error decoding output from jobId ", job.getJobId(), " as json: ", e));
                jobOutput = new HashMap<>();
            }
            final List<Failure> failures = JsonHelper.extractFailures(job);
            // Take
            final Failure failure = failures == null || failures.isEmpty() ? new Failure() : failures.get(failures.size() - 1);
            sendJobCompletionNotification(job.getWorker(), job, JobStatus.valueOf(job.getStatus()), jobOutput, failure);
        }
    }

    private void sendJobCompletionNotification(final String worker, final JobDBRecord job, final JobStatus status,
                                               final Object result, final Failure failure) {
        if (ComputeSpecification.MessageFormat.JSON.toString().equalsIgnoreCase(job.getJobStatusNotificationFormat())) {
            sendJobCompletionNotificationJson(job, status, result, failure);
        } else {
            sendJobCompletionNotificationLegacy(worker, job, status);
        }
    }

    private void sendJobCompletionNotificationJson(final JobDBRecord jobRecord, final JobStatus status,
                                                   final Object result, final Failure failure) {
        final Job job = new Job();
        job.setJobID(jobRecord.getJobId());
        job.setService(jobRecord.getService());
        job.setWorker(jobRecord.getWorker());
        job.setStatus(Status.valueOf(jobRecord.getStatus()));
        job.setTags(jobRecord.getTags());
        if (status.isCompleted()) {
            job.setResult(result);
        } else {
            final List<Failure> errors = new ArrayList<>();
            // we may have older errors in database, prepend the list to the current error.
            for (final String errorStr : Optional.ofNullable(jobRecord.getErrors()).orElse(new ArrayList<>())) {
                errors.add(JsonHelper.parseFailure(errorStr));
            }
            if (failure != null) {
                errors.add(failure);
            }
            job.setErrors(errors);
        }

        String message;
        try {
            message = Json.mapper.writeValueAsString(job);
        } catch (final JsonProcessingException e) {
            message = makeString("Could not construct sns message object for jobId ", jobRecord.getJobId());
            log.error(message);
        }
        final PublishRequest publishRequest = new PublishRequest(jobRecord.getJobStatusNotificationEndpoint(), message);
        publishSnsRequest(publishRequest, jobRecord);
        log.info(makeString("Send notification for jobId: ", job.getJobID()));
    }

    private void publishSnsRequest(final PublishRequest request, final JobDBRecord jobRecord) {
        final AmazonSNS snsClient = getSNSClient();
        try {
            snsClient.publish(request);
        } catch (final AmazonSNSException e) {
            log.warn(makeString("Error sending SNS notification for job ", jobRecord.getJobId(), " from ",
                    jobRecord.getService(), " / ", jobRecord.getWorker()), e);
        }
    }

    private void sendJobCompletionNotificationLegacy(final String worker, final JobDBRecord job, final JobStatus status) {
        final String message = status.toString();
        final Map<String, MessageAttributeValue> attributes = ImmutableMap.<String, MessageAttributeValue>builder()   //NOPMD
                .put("JobID", new MessageAttributeValue().withDataType("String").withStringValue(job.getJobId()))
                .put("Worker", new MessageAttributeValue().withDataType("String").withStringValue(worker)).build();
        final PublishRequest publishRequest = new PublishRequest(job.getJobStatusNotificationEndpoint(), message);
        publishRequest.setMessageAttributes(attributes);

        publishSnsRequest(publishRequest, job);
        log.info(makeString("Send notification for jobId: ", job.getJobId()));
    }

    private Failure extractFailure(final String jobId, final Map<String, Object> input) {
        @SuppressWarnings("squid:CommentedOutCodeLine")
        // So if there is an error object in the input, it has a format that gets
        // written into it from the SendTaskFailure API, namely:
        // "error": {
        //    "Error": "Test required failure",
        //    "Cause": "{}"
        // }
        final Object error = input.get("error");

        if (error == null) return null;

        @SuppressWarnings("squid:CommentedOutCodeLine") final
        // This is what it looks like: "{ "error": "some message", "details": { a-json-object } }"

        Failure failure = new Failure();

        // Convert error object to map so it's easier to work with.
        final LinkedHashMap<String, Object> failureMap = makeObjectToMap(error);

        final String errorField = failureMap.getOrDefault("Error", "").toString();

        final Object causeObj = failureMap.get("Cause");
        String causeStr = null;
        if (causeObj != null)
            causeStr = causeObj.toString();

        final JsonNode details = extractDetails(jobId, causeStr);
        failure.setError(errorField);
        failure.setDetails(details);
        final ZonedDateTime utc = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneOffset.UTC);
        // Remember when this error occurred
        failure.setTimestamp(utc.toString());
        return failure;
    }

    private LinkedHashMap<String, Object> makeObjectToMap(final Object obj) {
        final ObjectMapper jsonMapper = new ObjectMapper();
        final MapType mapType = jsonMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);
        return jsonMapper.convertValue(obj, mapType);
    }

    private JsonNode extractDetails(final String jobId, final String jsonStr) {
        JsonNode jsonObject;
        if (ComputeStringOps.isNullOrEmpty(jsonStr))
            jsonObject = Json.mapper.createObjectNode();
        else {
            try {
                final JsonNode parsedObject = Json.mapper.readTree(jsonStr);
                if (parsedObject.isObject())
                    jsonObject = parsedObject;
                else {
                    final ObjectNode objectNode = Json.mapper.createObjectNode();
                    objectNode.put("message", parsedObject.toString());
                    jsonObject = objectNode;
                }
            } catch (final JsonProcessingException e) {
                log.error(makeString("Failed to parse attribute from jobid ", jobId, " attribute was ", jsonStr));
                jsonObject = Json.mapper.createObjectNode();
            }
        }
        return jsonObject;
    }

    // This will crash immediately if run from main without arguments.
    // It is only here to validate configuration.
    public static void main(final String[] args) {
        new SFNCompletionHandler().handleRequest(new HashMap<>(), null);
    }

}
