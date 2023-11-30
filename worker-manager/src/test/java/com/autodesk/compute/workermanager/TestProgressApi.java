package com.autodesk.compute.workermanager;

import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.SendTaskHeartbeatRequest;
import com.amazonaws.services.stepfunctions.model.TaskTimedOutException;
import com.autodesk.compute.auth.basic.BasicAuthSecurityContext;
import com.autodesk.compute.auth.basic.BasicCredentials;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.model.Progress;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobCache;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.workermanager.api.impl.ProgressApiServiceImpl;
import com.autodesk.compute.workermanager.util.HeartbeatHandler;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.google.common.cache.LoadingCache;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

@Category(UnitTests.class)
@Slf4j
public class TestProgressApi {
    private static Progress progress;
    public static JobDBRecord jobDBRecord = new JobDBRecord();
    private final BasicAuthSecurityContext context = new BasicAuthSecurityContext(true,
            Optional.of(
                    new BasicCredentials("testService", "flintstone")));

    @Mock
    private DynamoDBJobClient dynamoDBJobClient;

    @Mock
    private AWSStepFunctions sfn;

    @Mock
    private Metrics metrics;

    @Mock
    private final LoadingCache<String, JobDBRecord> jobCache = JobCache.makeJobLoadingCache(300, 10, dynamoDBJobClient);

    @InjectMocks
    private HeartbeatHandler handler;

    private ProgressApiServiceImpl progressApi;

    private static void reinitialize() {
        progress = new Progress();
        progress.setJobID("jobId");
        progress.setJobSecret("secret");
        progress.setPercent(25);
        progress.setDetails("Details");

        jobDBRecord.setJobId("jobId");
        jobDBRecord.setService("testService");
    }

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
        handler = new HeartbeatHandler(dynamoDBJobClient, sfn, Response.Status.REQUEST_TIMEOUT, metrics, jobCache);
        progressApi = new ProgressApiServiceImpl(dynamoDBJobClient, handler, metrics, jobCache);
    }


    @BeforeClass
    public static void beforeClass() {
        reinitialize();
    }

    @After
    public void afterTest() {
        reinitialize();
    }

    @Test
    public void testProgressConstructorCoverage() {
        final ProgressApiServiceImpl impl = new ProgressApiServiceImpl();
    }

    @Test
    public void testProgressServiceNullJob() {
        jobDBRecord = null;
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_PROGRESS_UPDATE_ERROR),
                () -> progressApi.postProgress(progress, context)
        );
        jobDBRecord = new JobDBRecord();
    }

    @Test
    public void testProgressServiceOk() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        jobDBRecord.setBatch(false);
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        assertEquals(false, dynamoDBJobClient.isBatchJob(jobDBRecord.getJobId()));
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        assertEquals(200, progressApi.postProgress(progress, context).getStatus());
    }

    @Test
    public void testProgressServiceBatchOk() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        jobDBRecord.setBatch(true);
        jobDBRecord.setJobId("jobId");
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        assertEquals(200, progressApi.postProgress(progress, context).getStatus());
    }


    @Test
    public void testProgressServiceDynamoUpdateError() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.SERVER_UNEXPECTED, "error")).when(dynamoDBJobClient).updateProgress(anyString(), any(Integer.class), anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_PROGRESS_UPDATE_ERROR),
                () -> progressApi.postProgress(progress, context)
        );
    }

    @Test
    public void testProgressServiceDynamoUpdateConflictError() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN, "error")).when(dynamoDBJobClient).updateProgress(anyString(), any(Integer.class), anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN),
                () -> progressApi.postProgress(progress, context)
        );
    }

    @Test
    public void testProgressServiceDynamoBadRequest() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.AWS_BAD_REQUEST, "error")).when(dynamoDBJobClient).updateProgress(anyString(), any(Integer.class), anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_PROGRESS_UPDATE_ERROR),
                () -> progressApi.postProgress(progress, context)
        );
    }

    @Test
    public void testProgressServiceNoDetailsInInput() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        progress.setDetails(null);
        Mockito.doReturn(null).when(dynamoDBJobClient).updateItemSpec(anyString(), any(UpdateItemSpec.class), anyString(), anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        assertEquals(200, progressApi.postProgress(progress, context).getStatus());
    }

    @Test
    public void testProgressServiceNullPercent() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        progress.setPercent(null);
        Mockito.doReturn(null).when(dynamoDBJobClient).updateItemSpec(anyString(), any(UpdateItemSpec.class), anyString(), anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        assertEquals(200, progressApi.postProgress(progress, context).getStatus());
    }

    @Test
    public void testProgressActivityTimeoutError() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doReturn(null).when(dynamoDBJobClient).updateItemSpec(anyString(), any(UpdateItemSpec.class), anyString(), anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        Mockito.doAnswer(invocation -> {
            throw new TaskTimedOutException("Heartbeat");
        }).when(sfn).sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class));
        assertEquals(408, progressApi.postProgress(progress, context).getStatus());
    }

    @Test
    public void testProgressHeartbeatConflictError() throws ComputeException, WorkerManagerException, ExecutionException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doReturn(null).when(dynamoDBJobClient).updateItemSpec(anyString(), any(UpdateItemSpec.class), anyString(), anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        Mockito.doAnswer(invocation -> {
            throw new ComputeException(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN, "Heartbeat");
        }).when(sfn).sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class));
        validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN),
                () -> progressApi.postProgress(progress, context));
    }

    @Test
    public void testProgressServiceIncorrectAuth() throws WorkerManagerException, ExecutionException, ComputeException {
        final BasicAuthSecurityContext randomContext = new BasicAuthSecurityContext(true,
                Optional.of(
                        new BasicCredentials("randomService", "flintstone")));
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doReturn(null).when(dynamoDBJobClient).updateItemSpec(anyString(), any(UpdateItemSpec.class), anyString(), anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_AUTHORIZED),
                () -> progressApi.postProgress(progress, randomContext));
    }

    @Test
    public void testProgressServiceCorrectAuth() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doReturn(null).when(dynamoDBJobClient).updateItemSpec(anyString(), any(UpdateItemSpec.class), anyString(), anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        assertEquals(200, progressApi.postProgress(progress, context).getStatus());
    }

    @Test
    public void testProgressServiceComputeThrottleDuringGetJob() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doAnswer(invocation -> {
            throw new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity");
        }).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> progressApi.postProgress(progress, context));
    }

    @Test
    public void testProgressServiceComputeThrottleDuringUpdate() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity")).when(dynamoDBJobClient).updateProgress(anyString(), any(Integer.class), anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> progressApi.postProgress(progress, context));
    }

    @Test
    public void testProgressServiceComputeThrottleDuringUpdateHeartbeat() throws WorkerManagerException, ExecutionException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doNothing().when(metrics).reportProgressSent();
        Mockito.doNothing().when(metrics).reportHeartbeatSent();
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        Mockito.doAnswer(invocation -> {
            throw new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity");
        }).when(sfn).sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class));
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> progressApi.postProgress(progress, context));
    }


    @Test
    public void testProgressServiceCacheComputeExecutionException() throws ExecutionException {
        Mockito.doThrow(new ExecutionException(new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, "error"))).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> progressApi.postProgress(progress, context));
    }

    @Test
    public void testProgressServiceCacheUnknownExecutionException() throws ExecutionException {
        Mockito.doThrow(new ExecutionException(new RuntimeException("Ambiguous runtime error"))).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> progressApi.postProgress(progress, context));
    }


}