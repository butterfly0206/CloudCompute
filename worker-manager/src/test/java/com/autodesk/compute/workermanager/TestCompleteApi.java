package com.autodesk.compute.workermanager;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.InvalidOutputException;
import com.amazonaws.services.stepfunctions.model.SendTaskSuccessRequest;
import com.amazonaws.services.stepfunctions.model.TaskDoesNotExistException;
import com.amazonaws.services.stepfunctions.model.TaskTimedOutException;
import com.autodesk.compute.auth.basic.BasicAuthSecurityContext;
import com.autodesk.compute.auth.basic.BasicCredentials;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.Conclusion;
import com.autodesk.compute.common.model.Status;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobCache;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.workermanager.api.impl.CompleteApiServiceImpl;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Category(UnitTests.class)
@Slf4j
public class TestCompleteApi {
    public static Conclusion conclusion;

    public static JobDBRecord jobDBRecord;

    private final BasicAuthSecurityContext context = new BasicAuthSecurityContext(true,
            Optional.of(
                    new BasicCredentials("testService", "flintstone")));

    @Mock
    private AWSStepFunctions sfn;

    @Mock
    private DynamoDBJobClient dynamoDBJobClient;

    @Mock
    private Metrics metrics;

    @Mock
    private final LoadingCache<String, JobDBRecord> jobCache = JobCache.makeJobLoadingCache(300, 10, dynamoDBJobClient);

    CompleteApiServiceImpl completeApi;

    private static void reinitialize() {
        conclusion = new Conclusion();
        conclusion.setJobID("jobID");
        conclusion.setJobSecret("dG9rZW4=");
        conclusion.setStatus(Status.COMPLETED);
        conclusion.setDetails("details");
        conclusion.setError("no error");
        conclusion.setResult("result");
        conclusion.setTimestamp("CURRENT_TIME");

        jobDBRecord = JobDBRecord.createDefault("job00");
        jobDBRecord.setService("testService");
        jobDBRecord.setWorker("testworker");
        jobDBRecord.setBatch(false);

    }

    @BeforeClass
    public static void beforeClass() {
        reinitialize();
    }

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
        completeApi = new CompleteApiServiceImpl(sfn, dynamoDBJobClient, metrics, jobCache);
    }

    @After
    public void afterTest() {
        reinitialize();
    }

    @Test
    public void testCompleteApiConstructorCoverage() {
        final CompleteApiServiceImpl impl = new CompleteApiServiceImpl();
    }

    @Test
    public void testCompleteApiOKCompleted() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        assertEquals(200, completeApi.postComplete(conclusion, context).getStatus());
    }

    @Test
    public void testCompleteApiOKCompletedNoResult() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        conclusion.setResult("[]mkmcdlk");
        assertEquals(200, completeApi.postComplete(conclusion, context).getStatus());
    }

    @Test
    public void testCompleteApiOKFailed() throws WorkerManagerException, ComputeException, ExecutionException {
        conclusion.setStatus(Status.FAILED);
        Mockito.doNothing().when(metrics).reportJobFailed(anyString(), anyString());
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        assertEquals(200, completeApi.postComplete(conclusion, context).getStatus());
    }

    @Test
    public void testCompleteApiOKFailedNoError() throws WorkerManagerException, ComputeException, ExecutionException {
        conclusion.setStatus(Status.FAILED);
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doNothing().when(metrics).reportJobFailed(anyString(), anyString());
        conclusion.setError(null);
        assertEquals(200, completeApi.postComplete(conclusion, context).getStatus());
    }

    @Test
    public void testCompleteApiBatchOKCompleted() throws WorkerManagerException, ComputeException, ExecutionException {
        jobDBRecord.setBatch(true);
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        assertEquals(200, completeApi.postComplete(conclusion, context).getStatus());
    }

    @Test
    public void testCompleteApiBatchOKCompletedNoResult() throws WorkerManagerException, ComputeException, ExecutionException {
        jobDBRecord.setBatch(true);
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        conclusion.setResult("[]mkmcdlk");
        assertEquals(200, completeApi.postComplete(conclusion, context).getStatus());
    }

    @Test
    public void testCompleteApiBatchOKFailed() throws WorkerManagerException, ComputeException, ExecutionException {
        jobDBRecord.setBatch(true);
        conclusion.setStatus(Status.FAILED);
        Mockito.doNothing().when(metrics).reportJobFailed(anyString(), anyString());
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        assertEquals(200, completeApi.postComplete(conclusion, context).getStatus());
    }

    @Test
    public void testCompleteApiBatchOKFailedNoError() throws WorkerManagerException, ComputeException, ExecutionException {
        jobDBRecord.setBatch(true);
        conclusion.setStatus(Status.FAILED);
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doNothing().when(metrics).reportJobFailed(anyString(), anyString());
        conclusion.setError(null);
        assertEquals(200, completeApi.postComplete(conclusion, context).getStatus());
    }

    @Test
    public void testCompleteApiBatchOKCompletedThrottlingException() throws WorkerManagerException, ComputeException, ExecutionException {
        jobDBRecord.setBatch(true);
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity")).when(dynamoDBJobClient).markJobCompleted(nullable(String.class), nullable(List.class), nullable(String.class), nullable(String.class));
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> completeApi.postComplete(conclusion, context));
    }

    @Test
    public void testCompleteApiBatchOKFailedThrottlingException() throws WorkerManagerException, ComputeException, ExecutionException {
        jobDBRecord.setBatch(true);
        conclusion.setStatus(Status.FAILED);
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doNothing().when(metrics).reportJobFailed(anyString(), anyString());
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity")).when(dynamoDBJobClient).markJobFailed(nullable(String.class), nullable(String.class), nullable(List.class), nullable(String.class), nullable(String.class));
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> completeApi.postComplete(conclusion, context));
    }

    @Test
    public void testCompleteApiBatchOKExceptionCoverage() throws WorkerManagerException, ComputeException, ExecutionException {
        jobDBRecord.setBatch(true);
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        Mockito.doThrow(new RuntimeException("Arbitrary runtime error")).when(dynamoDBJobClient).markJobCompleted(nullable(String.class), nullable(List.class), nullable(String.class), nullable(String.class));
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> completeApi.postComplete(conclusion, context));
    }

    @Test
    public void testCompleteSFNActivityTimedOut() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        doThrow(new TaskTimedOutException("error")).when(sfn).sendTaskSuccess(any(SendTaskSuccessRequest.class));
        assertEquals(408, completeApi.postComplete(conclusion, context).getStatus());
    }

    @Test
    public void testCompleteSFNActivityNotFound() throws WorkerManagerException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        doThrow(new TaskDoesNotExistException("error")).when(sfn).sendTaskSuccess(any(SendTaskSuccessRequest.class));
        validateThrows(
                WorkerManagerException.class, new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_FOUND),
                () -> completeApi.postComplete(conclusion, context));
    }

    @Test
    public void testCompleteSFNActivityInvalidJobResult() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        doThrow(new InvalidOutputException("error")).when(sfn).sendTaskSuccess(any(SendTaskSuccessRequest.class));
        validateThrows(
                WorkerManagerException.class, new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.INVALID_JOB_RESULT),
                () -> completeApi.postComplete(conclusion, context));
    }

    @Test
    public void testcompleteSFNActivityComputeException() throws WorkerManagerException, ComputeException, ExecutionException {
        conclusion.setJobSecret("notBase64");
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class, new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.BAD_REQUEST),
                () -> completeApi.postComplete(conclusion, context));
    }

    @Test
    public void testCompleteApiOKAuthCompleted() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        assertEquals(200, completeApi.postComplete(conclusion, context).getStatus());
    }

    @Test
    public void testCompleteApiAuthFail() throws WorkerManagerException, ComputeException, ExecutionException {
        final BasicAuthSecurityContext randomContext = new BasicAuthSecurityContext(true,
                Optional.of(
                        new BasicCredentials("alternateService", "flintstone")));
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class, new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_AUTHORIZED),
                () -> completeApi.postComplete(conclusion, randomContext));
    }

    @Test
    public void testCompleteApiComputeExceptionCoverage() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doAnswer(invocation -> {
            throw new ComputeException(ComputeErrorCodes.SERVER, "Unknown ambiguous server error");
        }).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER),
                () -> completeApi.postComplete(conclusion, context));

    }

    @Test
    public void testCompleteApiComputeThrottleDuringGetJob() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doAnswer(invocation -> {
            throw new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity");
        }).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> completeApi.postComplete(conclusion, context));

    }

    @Test
    public void testCompleteApiThrottleDuringSfnSetTaskSuccessful() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doThrow(new AmazonClientException(ThrottleCheck.THROTTLING_STRINGS[1])).when(sfn).sendTaskSuccess(any(SendTaskSuccessRequest.class));
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> completeApi.postComplete(conclusion, context));

    }

    @Test
    public void testCompleteApiExceptionCoverageDuringSfnSetTaskSuccessful() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doThrow(new RuntimeException("Ambiguous runtime exception")).when(sfn).sendTaskSuccess(any(SendTaskSuccessRequest.class));
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> completeApi.postComplete(conclusion, context));
    }

    @Test
    public void testCompleteApiCacheComputeExecutionException() throws ExecutionException {
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        Mockito.doThrow(new ExecutionException(new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, "error"))).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> completeApi.postComplete(conclusion, context));
    }

    @Test
    public void testCompleteApiCacheUnknownExecutionException() throws ExecutionException {
        Mockito.doNothing().when(metrics).reportJobSucceeded(anyString(), anyString());
        Mockito.doThrow(new ExecutionException(new RuntimeException("Ambiguous runtime error"))).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> completeApi.postComplete(conclusion, context));
    }
}
