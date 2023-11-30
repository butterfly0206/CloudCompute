package com.autodesk.compute.completionhandler;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.PublishRequest;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.categories.UnitTests;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Category(UnitTests.class)
@Slf4j
public class TestSFNCompletionHandler {

    private static final String INPUT_FOR_SUCCESSFUL_JOB = "{\n" +
            "  \"jobId\": \"20fdd976-7f92-4848-9b2f-b278bfe37075\",\n" +
            "  \"worker\": \"fpccomp-c-uw2-sb-1-0-1627-sample1\",\n" +
            "  \"tags\": [\n" +
            "    \"simulation\",\n" +
            "    \"bicycle\"\n" +
            "  ],\n" +
            "  \"heartbeatTimeout\": 30,\n" +
            "  \"output\": {\n" +
            "    \"result\": {\n" +
            "      \"resultsProperty\": \"These are job results\"\n" +
            "    },\n" +
            "    \"error\": null,\n" +
            "    \"details\": null,\n" +
            "    \"timestamp\": \"2020-03-30T17:05:01.501Z\"\n" +
            "  },\n" +
            "  \"handleNotification\": {}\n" +
            "}";

    private static final String INPUT_FOR_FAILED_JOB = "{\n" +
            "  \"jobId\": \"e2856311-8f60-4699-b48b-4a3b2312a269\",\n" +
            "  \"worker\": \"fpccomp-c-uw2-sb-sample1\",\n" +
            "  \"tags\": [\n" +
            "    \"simulation\",\n" +
            "    \"bicycle\"\n" +
            "  ],\n" +
            "  \"heartbeatTimeout\": 30,\n" +
            "  \"error\": {\n" +
            "    \"Error\": \"{\\\"code\\\":\\\"500\\\",\\\"description\\\":\\\"Worker failure\\\",\\\"message\\\":\\\"This is a worker failure error message\\\",\\\"details\\\":{}}\",\n" +
            "    \"Cause\": \"{\\\"detailsProperty\\\":\\\"These are job error details\\\"}\"\n" +
            "  },\n" +
            "  \"handleNotification\": {},\n" +
            "  \"status\": \"FAILED\"\n" +
            "}";

    private static final String INPUT_FOR_FAILED_JOB_ACTIVITY_TIMEOUT = "{\n" +
            "  \"jobId\": \"e2856311-8f60-4699-b48b-4a3b2312a269\",\n" +
            "  \"worker\": \"fpccomp-c-uw2-sb-sample1\",\n" +
            "  \"tags\": [\n" +
            "    \"simulation\",\n" +
            "    \"bicycle\"\n" +
            "  ],\n" +
            "  \"heartbeatTimeout\": 30,\n" +
            "  \"error\": {\n" +
            "    \"Error\": \"States.Timeout\",\n" +
            "    \"Cause\": \"\"\n" +
            "  },\n" +
            "  \"handleNotification\": {},\n" +
            "  \"status\": \"FAILED\"\n" +
            "}";

    private static final String INPUT_FOR_FAILED_JOB_STRING_CAUSE = "{\n" +
            "  \"jobId\": \"e2856311-8f60-4699-b48b-4a3b2312a269\",\n" +
            "  \"worker\": \"fpccomp-c-uw2-sb-sample1\",\n" +
            "  \"tags\": [\n" +
            "    \"simulation\",\n" +
            "    \"bicycle\"\n" +
            "  ],\n" +
            "  \"heartbeatTimeout\": 30,\n" +
            "  \"error\": {\n" +
            "    \"Error\": \"{\\\"code\\\":\\\"500\\\",\\\"description\\\":\\\"Worker failure\\\",\\\"message\\\":\\\"This is a worker failure error message\\\",\\\"details\\\":{}}\",\n" +
            "    \"Cause\": \"\\\"These are job error details\\\"\"\n" +
            "  },\n" +
            "  \"handleNotification\": {},\n" +
            "  \"status\": \"FAILED\"\n" +
            "}";


    private static final String SFN_EVENT_FOR_SUCCESSFUL_JOB = "    {\n" +
            "    \"version\": \"0\",\n" +
            "    \"id\": \"21e970b3-68c1-ebfe-0993-aacfad0d3231\",\n" +
            "    \"detail-type\": \"Step Functions Execution Status Change\",\n" +
            "    \"source\": \"aws.states\",\n" +
            "    \"account\": \"718144611626\",\n" +
            "    \"time\": \"2020-07-31T03:28:59Z\",\n" +
            "    \"region\": \"us-west-2\",\n" +
            "    \"resources\": [\"arn:aws:states:us-west-2:718144611626:execution:fpcc_fpccomp-c-uw2-sb-sample4_step:3f1859bf-75e8-ba34-377d-f0505556692f\"],\n" +
            "    \"detail\": {\n" +
            "        \"executionArn\": \"arn:aws:states:us-west-2:718144611626:execution:fpcc_fpccomp-c-uw2-sb-sample4_step:3f1859bf-75e8-ba34-377d-f0505556692f\",\n" +
            "        \"stateMachineArn\": \"arn:aws:states:us-west-2:718144611626:stateMachine:fpcc_fpccomp-c-uw2-sb-sample4_step\",\n" +
            "        \"name\": \"3f1859bf-75e8-ba34-377d-f0505556692f\",\n" +
            "        \"status\": \"SUCCEEDED\",\n" +
            "        \"startDate\": 1596166093716,\n" +
            "        \"stopDate\": 1596166139232,\n" +
            "        \"input\": \"{\\\"jobId\\\":\\\"f9c62bdd-8eb2-4cb5-8a26-197fac819c9c\\\",\\\"worker\\\":\\\"fpccomp-c-uw2-sb-sample4\\\",\\\"tags\\\":[\\\"testLambdaJob\\\"],\\\"heartbeatTimeout\\\":30}\",\n" +
            "        \"output\": \"{\\n  \\\"jobId\\\": \\\"9c97af2f-7abb-43b1-a5af-792fb6871937\\\",\\n  \\\"worker\\\": \\\"fpccomp-c-uw2-sb-sample1\\\",\\n  \\\"tags\\\": [\\n    \\\"simulation\\\",\\n    \\\"bicycle\\\"\\n  ],\\n  \\\"heartbeatTimeout\\\": 30,\\n  \\\"output\\\": {\\n    \\\"result\\\": {\\n      \\\"resultsProperty\\\": \\\"These are job results\\\"\\n    },\\n    \\\"timestamp\\\": \\\"2020-07-28T17:54:10.851929Z\\\"\\n  },\\n  \\\"handleNotification\\\": {}\\n}\"\n" +
            "    }\n" +
            "    }";

    private static final String SFN_EVENT_FOR_FAILED_JOB = "    {\n" +
            "    \"version\": \"0\",\n" +
            "    \"id\": \"21e970b3-68c1-ebfe-0993-aacfad0d3231\",\n" +
            "    \"detail-type\": \"Step Functions Execution Status Change\",\n" +
            "    \"source\": \"aws.states\",\n" +
            "    \"account\": \"718144611626\",\n" +
            "    \"time\": \"2020-07-31T03:28:59Z\",\n" +
            "    \"region\": \"us-west-2\",\n" +
            "    \"resources\": [\"arn:aws:states:us-west-2:718144611626:execution:fpcc_fpccomp-c-uw2-sb-sample4_step:3f1859bf-75e8-ba34-377d-f0505556692f\"],\n" +
            "    \"detail\": {\n" +
            "        \"executionArn\": \"arn:aws:states:us-west-2:718144611626:execution:fpcc_fpccomp-c-uw2-sb-sample4_step:3f1859bf-75e8-ba34-377d-f0505556692f\",\n" +
            "        \"stateMachineArn\": \"arn:aws:states:us-west-2:718144611626:stateMachine:fpcc_fpccomp-c-uw2-sb-sample4_step\",\n" +
            "        \"name\": \"3f1859bf-75e8-ba34-377d-f0505556692f\",\n" +
            "        \"status\": \"FAILED\",\n" +
            "        \"startDate\": 1596166093716,\n" +
            "        \"stopDate\": 1596166139232,\n" +
            "        \"input\": \"{\\\"jobId\\\":\\\"f9c62bdd-8eb2-4cb5-8a26-197fac819c9c\\\",\\\"worker\\\":\\\"fpccomp-c-uw2-sb-sample4\\\",\\\"tags\\\":[\\\"testLambdaJob\\\"],\\\"heartbeatTimeout\\\":30}\",\n" +
            "        \"output\": null\n" +
            "    }\n" +
            "    }";

    private static final String SFN_EVENT_FOR_TIMED_OUT_JOB = "    {\n" +
            "    \"version\": \"0\",\n" +
            "    \"id\": \"21e970b3-68c1-ebfe-0993-aacfad0d3231\",\n" +
            "    \"detail-type\": \"Step Functions Execution Status Change\",\n" +
            "    \"source\": \"aws.states\",\n" +
            "    \"account\": \"718144611626\",\n" +
            "    \"time\": \"2020-07-31T03:28:59Z\",\n" +
            "    \"region\": \"us-west-2\",\n" +
            "    \"resources\": [\"arn:aws:states:us-west-2:718144611626:execution:fpcc_fpccomp-c-uw2-sb-sample4_step:3f1859bf-75e8-ba34-377d-f0505556692f\"],\n" +
            "    \"detail\": {\n" +
            "        \"executionArn\": \"arn:aws:states:us-west-2:718144611626:execution:fpcc_fpccomp-c-uw2-sb-sample4_step:3f1859bf-75e8-ba34-377d-f0505556692f\",\n" +
            "        \"stateMachineArn\": \"arn:aws:states:us-west-2:718144611626:stateMachine:fpcc_fpccomp-c-uw2-sb-sample4_step\",\n" +
            "        \"name\": \"3f1859bf-75e8-ba34-377d-f0505556692f\",\n" +
            "        \"status\": \"TIMED_OUT\",\n" +
            "        \"startDate\": 1596166093716,\n" +
            "        \"stopDate\": 1596166139232,\n" +
            "        \"input\": \"{\\\"jobId\\\":\\\"f9c62bdd-8eb2-4cb5-8a26-197fac819c9c\\\",\\\"worker\\\":\\\"fpccomp-c-uw2-sb-sample4\\\",\\\"tags\\\":[\\\"testLambdaJob\\\"],\\\"heartbeatTimeout\\\":30}\",\n" +
            "        \"output\": null\n" +
            "    }\n" +
            "    }  ";

    private static final String SFN_EVENT_FOR_CANCELED_JOB = "    {\n" +
            "    \"version\": \"0\",\n" +
            "    \"id\": \"21e970b3-68c1-ebfe-0993-aacfad0d3231\",\n" +
            "    \"detail-type\": \"Step Functions Execution Status Change\",\n" +
            "    \"source\": \"aws.states\",\n" +
            "    \"account\": \"718144611626\",\n" +
            "    \"time\": \"2020-07-31T03:28:59Z\",\n" +
            "    \"region\": \"us-west-2\",\n" +
            "    \"resources\": [\"arn:aws:states:us-west-2:718144611626:execution:fpcc_fpccomp-c-uw2-sb-sample4_step:3f1859bf-75e8-ba34-377d-f0505556692f\"],\n" +
            "    \"detail\": {\n" +
            "        \"executionArn\": \"arn:aws:states:us-west-2:718144611626:execution:fpcc_fpccomp-c-uw2-sb-sample4_step:3f1859bf-75e8-ba34-377d-f0505556692f\",\n" +
            "        \"stateMachineArn\": \"arn:aws:states:us-west-2:718144611626:stateMachine:fpcc_fpccomp-c-uw2-sb-sample4_step\",\n" +
            "        \"name\": \"3f1859bf-75e8-ba34-377d-f0505556692f\",\n" +
            "        \"status\": \"ABORTED\",\n" +
            "        \"startDate\": 1596166093716,\n" +
            "        \"stopDate\": 1596166139232,\n" +
            "        \"input\": \"{\\\"jobId\\\":\\\"f9c62bdd-8eb2-4cb5-8a26-197fac819c9c\\\",\\\"worker\\\":\\\"fpccomp-c-uw2-sb-sample4\\\",\\\"tags\\\":[\\\"testLambdaJob\\\"],\\\"heartbeatTimeout\\\":30}\",\n" +
            "        \"output\": null\n" +
            "    }\n" +
            "    }  ";


    private final TestContext testContext = new TestContext();

    @Mock
    private DynamoDBJobClient dynamoDBJobClient;

    @Mock
    private AmazonSNS snsClient;

    @Spy
    @InjectMocks
    private SFNCompletionHandler handler;

    private ObjectMapper mapper;

    @Before
    public void beforeTest() {
        MockitoAnnotations.openMocks(this);
        mapper = new ObjectMapper();
        doReturn(dynamoDBJobClient).when(handler).getDynamoDBJobClient();
    }

    private static JobDBRecord buildJobRecord() {
        // build a job record
        final JobDBRecord jobRecord = JobDBRecord.createDefault("20fdd976-7f92-4848-9b2f-b278bfe37075");
        jobRecord.setStatus(JobStatus.SCHEDULED.toString());
        jobRecord.setTags(Collections.singletonList("test"));
        jobRecord.setPercentComplete(0);
        jobRecord.setBinaryPayload(ComputeStringOps.compressString("{}"));
        jobRecord.setOxygenToken("oxygenToken");
        jobRecord.setHeartbeatStatus(DynamoDBJobClient.HeartbeatStatus.ENABLED.toString());
        jobRecord.setHeartbeatTimeoutSeconds(120);
        jobRecord.setCurrentRetry(0);
        jobRecord.setLastHeartbeatTime(0L);
        jobRecord.setCreationTime(JobDBRecord.fillCurrentTimeMilliseconds());
        jobRecord.setService("service");
        jobRecord.setPortfolioVersion("1.0");
        jobRecord.setWorker("worker");
        jobRecord.setJobFinishedTime(0L);
        jobRecord.setIdempotencyId("idempotencyId");
        jobRecord.setStepExecutionArns(Arrays.asList("someArn"));
        jobRecord.setResult("{}");
        return jobRecord;
    }

    private HashMap<String, Object> getLambdaInputSuccessfulJob() throws JsonProcessingException {
        return mapper.readValue(INPUT_FOR_SUCCESSFUL_JOB, HashMap.class);
    }

    private HashMap<String, Object> getLambdaInputFailedJob() throws JsonProcessingException {
        return mapper.readValue(INPUT_FOR_FAILED_JOB, HashMap.class);
    }

    private HashMap<String, Object> getLambdaInputFailedJobActivityTimeout() throws JsonProcessingException {
        return mapper.readValue(INPUT_FOR_FAILED_JOB_ACTIVITY_TIMEOUT, HashMap.class);
    }

    private HashMap<String, Object> getLambdaInputFailedJobStringCause() throws JsonProcessingException {
        return mapper.readValue(INPUT_FOR_FAILED_JOB_STRING_CAUSE, HashMap.class);
    }

    private HashMap<String, Object> getLambdaInputSfnEventSuccessfulJob() throws JsonProcessingException {
        return mapper.readValue(SFN_EVENT_FOR_SUCCESSFUL_JOB, HashMap.class);
    }

    private HashMap<String, Object> getLambdaInputSfnEventFailedJob() throws JsonProcessingException {
        return mapper.readValue(SFN_EVENT_FOR_FAILED_JOB, HashMap.class);
    }

    private HashMap<String, Object> getLambdaInputSfnEventTimeOutJob() throws JsonProcessingException {
        return mapper.readValue(SFN_EVENT_FOR_TIMED_OUT_JOB, HashMap.class);
    }

    private HashMap<String, Object> getLambdaInputSfnEventCanceledJob() throws JsonProcessingException {
        return mapper.readValue(SFN_EVENT_FOR_CANCELED_JOB, HashMap.class);
    }

    @Test
    public void testHandlerSuccessfulJobOk() throws ComputeException, JsonProcessingException {
        final JobDBRecord jobRecord = buildJobRecord();
        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobCompleted(anyString(), any(List.class), anyString(), anyString());
        handler.handleRequest(getLambdaInputSuccessfulJob(), testContext);

        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));

        // verify dynamoDBJobClient.markJobFailed was never called
        verify(dynamoDBJobClient, never()).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));

    }


    @Test
    public void testHandlerSuccessfulJobSfnEventOk() throws ComputeException, JsonProcessingException {

        final JobDBRecord jobRecord = buildJobRecord();
        jobRecord.setJobStatusNotificationEndpoint("some:endpoint");
        jobRecord.setJobStatusNotificationType("sns");
        jobRecord.setJobStatusNotificationFormat("json");
        doReturn(snsClient).when(handler).getSNSClient();

        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobCompleted(anyString(), any(List.class), anyString(), anyString());
        handler.handleRequest(getLambdaInputSfnEventSuccessfulJob(), testContext);

        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));
        // verify dynamoDBJobClient.markJobFailed was never called
        verify(dynamoDBJobClient, never()).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));
        // Validate if sns publish was called exactly once
        verify(snsClient, times(1)).publish(any(PublishRequest.class));
    }


    @Test
    public void testHandlerFailedJobOk() throws ComputeException, JsonProcessingException {

        final JobDBRecord jobRecord = buildJobRecord();

        // Job is already marked failed by worker manager
        jobRecord.setStatus(JobStatus.FAILED.toString());

        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobFailed(anyString(), anyString(), any(List.class), anyString(), anyString());
        handler.handleRequest(getLambdaInputFailedJob(), testContext);

        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));

        // verify dynamoDBJobClient.markJobFailed was never called
        verify(dynamoDBJobClient, never()).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));
    }

    @Test
    public void testHandlerFailedJobActivityTimeoutOk() throws ComputeException, JsonProcessingException {

        final JobDBRecord jobRecord = buildJobRecord();

        // In case of activity timeout job will be in non terminal state.
        jobRecord.setStatus(JobStatus.INPROGRESS.toString());

        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobFailed(anyString(), anyString(), any(List.class), anyString(), anyString());
        handler.handleRequest(getLambdaInputFailedJobActivityTimeout(), testContext);

        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));

        //
        // verify dynamoDBJobClient.markJobFailed was called once
        verify(dynamoDBJobClient, times(1)).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));
    }

    @Test
    public void testHandlerFailedJobSfnEventOk() throws ComputeException, JsonProcessingException {

        final JobDBRecord jobRecord = buildJobRecord();
        jobRecord.setJobStatusNotificationEndpoint("some:endpoint");
        jobRecord.setJobStatusNotificationType("sns");
        jobRecord.setJobStatusNotificationFormat("json");
        doReturn(snsClient).when(handler).getSNSClient();

        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobFailed(anyString(), anyString(), any(List.class), anyString(), anyString());
        handler.handleRequest(getLambdaInputSfnEventFailedJob(), testContext);

        // verify dynamoDBJobClient.markJobFailed was called onc
        // we expect job failure since the SFN failed
        verify(dynamoDBJobClient, times(1)).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));
        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));
        // Validate if sns publish was called exactly once
        verify(snsClient, times(1)).publish(any(PublishRequest.class));
    }

    @Test
    public void testHandlerTimedOutJobSfnEventOk() throws ComputeException, JsonProcessingException {

        final JobDBRecord jobRecord = buildJobRecord();
        jobRecord.setJobStatusNotificationEndpoint("some:endpoint");
        jobRecord.setJobStatusNotificationType("sns");
        jobRecord.setJobStatusNotificationFormat("json");
        doReturn(snsClient).when(handler).getSNSClient();

        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobFailed(anyString(), anyString(), any(List.class), anyString(), anyString());
        handler.handleRequest(getLambdaInputSfnEventTimeOutJob(), testContext);

        // verify dynamoDBJobClient.markJobFailed was called exactly once
        verify(dynamoDBJobClient, times(1)).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));
        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));
        // Validate if sns publish was called exactly once
        verify(snsClient, times(1)).publish(any(PublishRequest.class));
    }

    @Test
    public void testHandlerCanceledJobSfnEventOk() throws ComputeException, JsonProcessingException {
        final JobDBRecord jobRecord = buildJobRecord();
        jobRecord.setJobStatusNotificationEndpoint("some:endpoint");
        jobRecord.setJobStatusNotificationType("sns");
        jobRecord.setJobStatusNotificationFormat("json");
        doReturn(snsClient).when(handler).getSNSClient();

        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobFailed(anyString(), anyString(), any(List.class), anyString(), anyString());
        handler.handleRequest(getLambdaInputSfnEventCanceledJob(), testContext);

        // verify dynamoDBJobClient.markJobFailed was never called
        verify(dynamoDBJobClient, never()).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));
        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));
        // Validate if sns publish was called exactly once
        verify(snsClient, times(1)).publish(any(PublishRequest.class));
    }

    @Test
    public void testHandlerFailedStringCauseJobOk() throws ComputeException, JsonProcessingException {
        final HashMap<String, Object> validLambdaInput = getLambdaInputFailedJobStringCause();
        final JobDBRecord jobRecord = buildJobRecord();

        // Job is already marked failed by worker manager
        jobRecord.setStatus(JobStatus.FAILED.toString());

        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobFailed(anyString(), anyString(), any(List.class), anyString(), anyString());
        handler.handleRequest(validLambdaInput, testContext);

        // verify dynamoDBJobClient.markJobFailed was never called
        verify(dynamoDBJobClient, never()).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));

        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));
    }


    @Test
    public void testHandlerWithSNSNotificationLegacyOk() throws ComputeException, JsonProcessingException {
        final JobDBRecord jobRecord = buildJobRecord();
        jobRecord.setJobStatusNotificationEndpoint("some:endpoint");
        jobRecord.setJobStatusNotificationType("sns");

        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobCompleted(anyString(), any(List.class), anyString(), anyString());
        doReturn(snsClient).when(handler).getSNSClient();

        handler.handleRequest(getLambdaInputSfnEventSuccessfulJob(), testContext);

        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));

        // verify dynamoDBJobClient.markJobFailed was never called
        verify(dynamoDBJobClient, never()).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));

        // Validate if sns publish was called exactly once
        verify(snsClient, times(1)).publish(any(PublishRequest.class));
    }

    @Test
    public void testHandlerWithSNSNotificationJsonOk() throws ComputeException, JsonProcessingException {
        final JobDBRecord jobRecord = buildJobRecord();
        jobRecord.setJobStatusNotificationEndpoint("some:endpoint");
        jobRecord.setJobStatusNotificationType("sns");
        jobRecord.setJobStatusNotificationFormat("json");

        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobCompleted(anyString(), any(List.class), anyString(), anyString());
        doReturn(snsClient).when(handler).getSNSClient();

        handler.handleRequest(getLambdaInputSfnEventFailedJob(), testContext);

        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));

        // verify dynamoDBJobClient.markJobFailed was called once
        // we expect job failure since the SFN failed
        verify(dynamoDBJobClient, times(1)).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));

        // Validate if sns publish was called exactly once
        verify(snsClient, times(1)).publish(any(PublishRequest.class));
    }

    @Test
    public void testHandlerWithSNSNotificationJsonFailedJobOk() throws ComputeException, JsonProcessingException {
        final JobDBRecord jobRecord = buildJobRecord();
        jobRecord.setJobStatusNotificationEndpoint("some:endpoint");
        jobRecord.setJobStatusNotificationType("sns");
        jobRecord.setJobStatusNotificationFormat("json");
        jobRecord.setErrors(Arrays.asList("some old errors"));

        doReturn(jobRecord).when(dynamoDBJobClient).getJob(anyString());
        doNothing().when(dynamoDBJobClient).markJobCompleted(anyString(), any(List.class), anyString(), anyString());
        doReturn(snsClient).when(handler).getSNSClient();

        handler.handleRequest(getLambdaInputSfnEventSuccessfulJob(), testContext);

        // verify dynamoDBJobClient.markJobFailed was never called
        verify(dynamoDBJobClient, never()).markJobFailed(nullable(String.class), nullable(String.class),
                nullable(List.class), nullable(String.class), nullable(String.class));

        // verify dynamoDBJobClient.markJobCompleted was never called
        verify(dynamoDBJobClient, never()).markJobCompleted(nullable(String.class), nullable(List.class),
                nullable(String.class), nullable(String.class));

        // Validate if sns publish was called exactly once
        verify(snsClient, times(1)).publish(any(PublishRequest.class));
    }

}
