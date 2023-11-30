package com.autodesk.compute.workermanager;

import com.amazonaws.AmazonClientException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.*;
import com.autodesk.compute.auth.basic.BasicAuthSecurityContext;
import com.autodesk.compute.auth.basic.BasicCredentials;
import com.autodesk.compute.common.CloudWatchClient;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.PollResponse;
import com.autodesk.compute.common.model.Status;
import com.autodesk.compute.common.model.Worker;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.discovery.ServiceResources;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.model.cosv2.ComputeSpecification;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.workermanager.api.impl.PollApiServiceImpl;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Optional;

import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.*;

@Category(UnitTests.class)
@Slf4j
public class TestPollApi
{
    private static final Worker testPollData = new Worker();
    private static final GetActivityTaskResult activityTaskResult = new GetActivityTaskResult();
    public static JobDBRecord jobDBRecord;
    private final BasicAuthSecurityContext context = new BasicAuthSecurityContext(true,
            Optional.of(
                    new BasicCredentials("testService", "flintstone")));

    @Mock
    AWSStepFunctions sfn;

    @Mock
    Metrics metrics;

    @Mock
    DynamoDBJobClient dynamoDBJobClient;

    @Spy
    @InjectMocks
    PollApiServiceImpl pollService;

    @Mock
    ServiceResources.WorkerResources workerResource;

    @Mock
    ServiceResources.BatchWorkerResources batchWorkerResource;

    @Mock
    ComputeSpecification computeSpec;

    @Mock
    SecurityContext securityContext;

    @Mock
    CloudWatchClient cloudWatchClient;

    @BeforeClass
    public static void beforeClass()  {
        reinitialize();
    }

    @After
    public void afterTest() {
        reinitialize();
    }

    private static void reinitialize() {
        jobDBRecord = JobDBRecord.createDefault("job00");
        jobDBRecord.setService("testService");
        jobDBRecord.setWorker("testworker");
        jobDBRecord.setBatch(false);
        jobDBRecord.setBinaryPayload(ComputeStringOps.compressString("{\"payload\": \"some data\"}"));
        jobDBRecord.setTags(Arrays.asList("tag1", "tag2"));
        jobDBRecord.setStatusUpdates(Lists.newArrayList());
        testPollData.setService("testService");
        testPollData.setWorker("pathg-gen");

        activityTaskResult.setTaskToken("token");
        activityTaskResult.setInput("{\"jobId\":\"jobId\",\"worker\":\"workers\",\"tags\":null}");

    }

    @Test
    public void testPollServiceOk() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(metrics).reportJobScheduledDuration(anyString(), anyString(), anyDouble());
        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());

        final Response response = pollService.pollForJob(testPollData, context);
        assertEquals(200, response.getStatus());

        assertNotNull(response.getEntity());
        assertEquals(PollResponse.class, response.getEntity().getClass());
        final PollResponse pollResponse = (PollResponse) response.getEntity();
        assertNotNull(pollResponse);
        assertEquals(2, pollResponse.getTags().size());
    }

    @Test
    public void testPollServiceOkWithStatusUpdates() throws JsonProcessingException, WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(metrics).reportJobScheduledDuration(anyString(), anyString(), anyDouble());

        jobDBRecord.setStatusUpdates(Arrays.asList(JobDBRecord.getJobStatusUpdateAsString(Status.QUEUED.toString()),
                JobDBRecord.getJobStatusUpdateAsString(Status.SCHEDULED.toString())));

        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());

        final Response response = pollService.pollForJob(testPollData, context);
        assertEquals(200, response.getStatus());

        assertNotNull(response.getEntity());
        assertEquals(PollResponse.class, response.getEntity().getClass());
        final PollResponse pollResponse = (PollResponse) response.getEntity();
        assertNotNull(pollResponse);
        assertEquals(2, pollResponse.getTags().size());
    }

    @Test
    public void testPollServiceNullJobID() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        activityTaskResult.setInput("{}"); // valid garbage json input

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceBlankJobID() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        activityTaskResult.setInput("{\"jobId\":\"\",\"payload\":\"payload\",\"worker\":\"workers\",\"tags\":null}");

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollParseException() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        String input = null;
        input = activityTaskResult.getInput();
        activityTaskResult.setInput("{bad-json");
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceInvalidJobPayload() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doThrow(new WorkerManagerException(ComputeErrorCodes.INVALID_JOB_PAYLOAD, "error")).when(pollService)
                .getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.INVALID_JOB_PAYLOAD),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceRateLimit() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        Mockito.doThrow(new ActivityWorkerLimitExceededException("error")).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService)
                .getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_BUSY),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceInvalidArnException() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        Mockito.doThrow(new InvalidArnException("error")).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService)
                .getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceActivityDoesNotExistException() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        Mockito.doThrow(new ActivityDoesNotExistException("error")).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService)
                .getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceSdkClientException() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        Mockito.doThrow(new SdkClientException("error")).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService)
                .getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.BAD_REQUEST),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceSocketTimeoutException() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        final SdkClientException e = new SdkClientException("error", new SocketTimeoutException());
        Mockito.doThrow(e).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService)
                .getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_NOT_FOUND),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceNullTaskToken() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        activityTaskResult.setTaskToken(null);
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService)
                .getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_NOT_FOUND),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceBlankTaskToken() throws WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        activityTaskResult.setTaskToken("");
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService)
                .getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();

        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_NOT_FOUND),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceIncorrectAuth() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        final BasicAuthSecurityContext randomContext = new BasicAuthSecurityContext(true,
                Optional.of(
                        new BasicCredentials("test-moniker", "flintstone")));

        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        testPollData.setService("test-service-auth");
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_AUTHORIZED),
                () -> pollService.pollForJob(testPollData, randomContext));
    }

    @Test
    public void testPollServiceCorrectAuth() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);

        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        final Response response = pollService.pollForJob(testPollData, context);
        assertEquals(200,response.getStatus());
    }

    @Test
    public void testPollServiceNoAuthEnforced() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        final BasicAuthSecurityContext contextNoAuthEnforced = new BasicAuthSecurityContext(false, Optional.empty());
        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        final Response response = pollService.pollForJob(testPollData, contextNoAuthEnforced);
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testPollServiceComputeNullDuringGetJob() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(metrics).reportJobScheduledDuration(anyString(), anyString(), anyDouble());
        Mockito.doReturn(null).when(dynamoDBJobClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceComputeThrottleDuringGetJob() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(metrics).reportJobScheduledDuration(anyString(), anyString(), anyDouble());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity")).when(dynamoDBJobClient).getJob(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceComputeExceptionCoverage() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(metrics).reportJobScheduledDuration(anyString(), anyString(), anyDouble());
        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.SERVER, "Unknown ambiguous server error")).when(dynamoDBJobClient).markJobInprogress(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceThrottlingCoverage() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doThrow(new AmazonClientException(ThrottleCheck.THROTTLING_STRINGS[1])).when(metrics).reportJobScheduledDuration(anyString(), anyString(), anyDouble());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceComputeThrottleDuringGetActivityTask() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(metrics).reportJobScheduledDuration(anyString(), anyString(), anyDouble());
        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doThrow(new AmazonClientException(ThrottleCheck.THROTTLING_STRINGS[1])).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> pollService.pollForJob(testPollData, context));
    }

    @Test
    public void testPollServiceComputeThrottleDuringProgressUpdate() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(metrics).reportJobScheduledDuration(anyString(), anyString(), anyDouble());
        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doReturn(false).when(computeSpec).getUseServiceIntegration();
        Mockito.doReturn(activityTaskResult).when(sfn).getActivityTask(any(GetActivityTaskRequest.class));
        Mockito.doReturn(workerResource).when(pollService).getWorkerResource(anyString(), anyString(), isNull(), anyBoolean());
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity")).when(dynamoDBJobClient).markJobInprogress(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> pollService.pollForJob(testPollData, context));
    }
}
