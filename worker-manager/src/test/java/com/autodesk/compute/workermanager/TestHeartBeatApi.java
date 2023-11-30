package com.autodesk.compute.workermanager;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.AWSStepFunctionsException;
import com.amazonaws.services.stepfunctions.model.SendTaskHeartbeatRequest;
import com.amazonaws.services.stepfunctions.model.TaskDoesNotExistException;
import com.amazonaws.services.stepfunctions.model.TaskTimedOutException;
import com.autodesk.compute.auth.basic.BasicAuthSecurityContext;
import com.autodesk.compute.auth.basic.BasicCredentials;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.Heartbeat;
import com.autodesk.compute.common.model.HeartbeatResponse;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobCache;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.TestHelper;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.workermanager.api.impl.HeartbeatApiServiceImpl;
import com.autodesk.compute.workermanager.util.HeartbeatHandler;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.google.common.cache.LoadingCache;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.amazonaws.util.ValidationUtils.assertNotNull;
import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Category(UnitTests.class)
@Slf4j
public class TestHeartBeatApi {
    private static final Heartbeat data = new Heartbeat();
    public JobDBRecord jobDBRecord = new JobDBRecord();
    private final BasicAuthSecurityContext context = new BasicAuthSecurityContext(true,
            Optional.of(
                    new BasicCredentials("testService", "flintstone")));

    @Mock
    private Metrics metrics;

    @Mock
    private AWSStepFunctions sfn;

    @Mock
    private DynamoDBJobClient dynamoDBJobClient;

    @Mock
    private final LoadingCache<String, JobDBRecord> jobCache = JobCache.makeJobLoadingCache(300, 10, dynamoDBJobClient);

    private HeartbeatHandler handler;
    private HeartbeatApiServiceImpl heartBeatService;


    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
        handler = new HeartbeatHandler(dynamoDBJobClient, sfn, Response.Status.REQUEST_TIMEOUT, metrics, jobCache);
        heartBeatService = new HeartbeatApiServiceImpl(handler);

        data.setJobID("jobId");
        data.setJobSecret("dG9rZW4=");
        jobDBRecord.setJobId("jobId");
        jobDBRecord.setService("testService");
    }

    @Test
    public void testHeartBeatOk() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        assertEquals(200, heartBeatService.postHeartbeat(data, context).getStatus());
    }

    @Test
    public void testHeartBeatBadSecret() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        data.setJobSecret("%NotB64String%");
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        TestHelper.validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.BAD_REQUEST),
                () -> heartBeatService.postHeartbeat(data, context).getStatus()
        );
    }

    @Test
    public void testHeartBeatHandlerConstructorCoverage() {
        assertNotNull(new HeartbeatHandler(), "Default constructor failed");
        assertNotNull(new HeartbeatHandler(Response.Status.REQUEST_TIMEOUT), "Simple response constructor failed");
    }

    @Test
    public void testHeartBeatHandlerNullJobId() throws WorkerManagerException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        TestHelper.validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER),
                () -> handler.processHeartbeat((String) null, data.getJobSecret(), context)
        );
    }

    @Test
    public void testHeartBeatHandlerNullJobRecord() throws WorkerManagerException, ComputeException {
        Mockito.doReturn(jobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        TestHelper.validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER),
                () -> handler.processHeartbeat((JobDBRecord) null, data.getJobSecret(), context)
        );
    }

    @Test
    public void testHeartBeatHandlerNullJobIdInRecord() throws WorkerManagerException, ComputeException, ExecutionException {
        final JobDBRecord nullJob = new JobDBRecord();
        Mockito.doReturn(nullJob).when(jobCache).get(anyString());
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        TestHelper.validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.BAD_REQUEST),
                () -> handler.processHeartbeat(nullJob, data.getJobSecret(), context)
        );
    }

    @Test
    public void testHeartBeatHandlerNullJobSecret() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        TestHelper.validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.BAD_REQUEST),
                () -> handler.processHeartbeat(data.getJobID(), (String) null, context)
        );
    }

    @Test
    public void testHeartBeatHandlerUnknownExceptionCoverage() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doThrow(new ExecutionException(new RuntimeException("Ambiguous execution error"))).when(jobCache).get(anyString());
        TestHelper.validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> handler.processHeartbeat(data.getJobID(), data.getJobSecret(), context)
        );
    }

    @Test
    public void testHeartBeatHandlerCacheThrottlingExceptionCoverage() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doThrow(new ExecutionException(new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity"))).when(jobCache).get(anyString());
        TestHelper.validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> handler.processHeartbeat(data.getJobID(), data.getJobSecret(), context)
        );
    }

    @Test
    public void testHeartBeatTimeOut() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenThrow(new TaskTimedOutException("error"));
        final HeartbeatResponse heartbeatResponse = (HeartbeatResponse) heartBeatService.postHeartbeat(data, context).getEntity();
        assertEquals(true, heartbeatResponse.getCanceled());
    }

    @Test
    public void testHeartBeatStepFnFail() throws ComputeException {
        Mockito.doReturn(null).when(dynamoDBJobClient).updateItemSpec(anyString(), any(UpdateItemSpec.class), anyString(), anyString());
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenThrow(new AWSStepFunctionsException("error"));
        TestHelper.validateThrows(
                WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_FOUND),
                () -> heartBeatService.postHeartbeat(data, context).getEntity()
        );
    }

    @Test
    public void testHeartBeatIncorrectAuth() throws ComputeException, ExecutionException {
        final BasicAuthSecurityContext randomContext = new BasicAuthSecurityContext(true,
                Optional.of(
                        new BasicCredentials("randomService", "flintstone")));
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        jobDBRecord.setService("testService");
        when(sfn.sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class))).thenReturn(null);
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_AUTHORIZED),
                () -> heartBeatService.postHeartbeat(data, randomContext));
    }

    @Test
    public void testHeartBeatComputeThrottleDuringGetJob() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doAnswer(invocation -> {
            throw new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity");
        }).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> heartBeatService.postHeartbeat(data, context));
    }

    @Test
    public void testHeartBeatThrottleDuringSendTaskHeartbeat() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doThrow(new AmazonClientException(ThrottleCheck.THROTTLING_STRINGS[1])).when(sfn).sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class));
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> heartBeatService.postHeartbeat(data, context));
    }

    @Test
    public void testHeartBeatExceptionCoverageDuringSendTaskHeartbeat() throws WorkerManagerException, ComputeException, ExecutionException {
        Mockito.doReturn(jobDBRecord).when(jobCache).get(anyString());
        Mockito.doThrow(new TaskDoesNotExistException("Task does not exist")).when(sfn).sendTaskHeartbeat(any(SendTaskHeartbeatRequest.class));
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_FOUND),
                () -> heartBeatService.postHeartbeat(data, context));
    }


}
