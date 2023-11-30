package com.autodesk.compute.workermanager;

import com.autodesk.compute.common.OxygenClient;
import com.autodesk.compute.common.model.AcquireTokenArguments;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobCache;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.Matchers.ExceptionCodeMatcher;
import com.autodesk.compute.test.Matchers.MessageMatcher;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.workermanager.api.impl.TokenApiServiceImpl;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.common.ComputeStringOps.compressString;
import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.hamcrest.core.CombinableMatcher.both;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@Category(UnitTests.class)
@Slf4j
public class TestTokenApi {
    public static final String JOB_PAYLOAD = "{ \"Empty\": \"Payload\" }";
    public static final String JOB_RESULT = "{ \"Hello\": \"World\" }";
    public static final String JOB_DETAILS = "{ \"Details\": \"Unimportant\" }";
    public static final String JOB_RESULT_WITH_RESULT = "{ \"result\": { \"Hello\": \"World\" } }";
    public static final String JOB_ID = "aaaaaaaa-bbbb-cccc-dddd-abcdefabcdef";
    public static final List<String> JOB_TAGS = Arrays.asList("a one", "a two", "a one two", "three four");
    public static final List<String> JOB_ERRORS = Arrays.asList(
            "{ \"not a valid error \" }",
            "{ \"error\": \"no details\" }",
            "{ \"error\": \"with details\", \"details\": \"minimal\" }",
            "{ \"error\": \"with details\", \"details\": \"bogus timestamp\", \"timestamp\": \"{ \"huh\": \"for code coverage\" }\" }",
            "{ \"error\": \"with details\", \"details\": \"has stringified long timestamp\", \"timestamp\": \"600\" }",
            "{ \"error\": \"with details\", \"details\": \"has int timestamp\", \"timestamp\": 102834600 }",
            "{ \"error\": \"with details\", \"details\": \"has long timestamp\", \"timestamp\": 102834600000 }",
            "{ \"error\": \"with details\", \"details\": \"has iso timestamp\", \"timestamp\": \"2020-02-25T06:15:00Z\" }"
    );

    @Mock
    private DynamoDBJobClient dynamoDBJobClient;

    @Mock
    private OxygenClient oxygenClient;

    @Mock
    private ComputeConfig computeConfig;

    @Mock
    private final LoadingCache<String, JobDBRecord> jobCache = JobCache.makeJobLoadingCache(300, 10, dynamoDBJobClient);

    @Spy
    @InjectMocks
    private TokenApiServiceImpl tokenApi;

    private static AcquireTokenArguments acquireTokenArguments;

    private static AcquireTokenArguments nullRefresh;

    @BeforeClass
    public static void beforeClass() {
        acquireTokenArguments = new AcquireTokenArguments();
        acquireTokenArguments.setJobId("jobId");
        acquireTokenArguments.setJobSecret("jobSecret");
        acquireTokenArguments.setRefresh(true);

        nullRefresh = new AcquireTokenArguments();
        nullRefresh.setJobId("jobId");
        nullRefresh.setJobSecret("jobSecret");
    }

    @Test
    public void testTokenConstructorCoverage() {
        TokenApiServiceImpl impl = new TokenApiServiceImpl();
        impl = new TokenApiServiceImpl(ComputeConfig.getInstance());
    }

    @Test
    public void testGetTokenOk() throws ComputeException, WorkerManagerException, ExecutionException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn("token").when(oxygenClient).extendToken(anyString());
        Mockito.doReturn(buildCompletedJobWithStatus(JobStatus.INPROGRESS)).when(jobCache).get(anyString());

        assertEquals(200, tokenApi.getToken("jobID", "token", true, null).getStatus());
        verify(oxygenClient, times(1)).extendToken(anyString());
        verify(dynamoDBJobClient, times(1)).updateToken(anyString(), anyString());
    }

    @Test
    public void testGetTokenOkNoRefresh() throws ComputeException, WorkerManagerException, ExecutionException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn("token").when(oxygenClient).extendToken(anyString());
        Mockito.doReturn(buildCompletedJobWithStatus(JobStatus.INPROGRESS)).when(jobCache).get(anyString());

        assertEquals(200, tokenApi.getToken("jobID", "token", false, null).getStatus());
        verify(oxygenClient, times(0)).extendToken(anyString());
        verify(dynamoDBJobClient, times(0)).updateToken(anyString(), anyString());
    }

    @Test
    public void testGetTokenAfterCompleted() throws ComputeException, WorkerManagerException, ExecutionException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn("token").when(dynamoDBJobClient).getToken(anyString());
        Mockito.doReturn("token").when(oxygenClient).extendToken(anyString());
        Mockito.doReturn(buildCompletedJobWithStatus(JobStatus.COMPLETED)).when(jobCache).get(anyString());

        validateThrows(WorkerManagerException.class,
                both(new ExceptionCodeMatcher(ComputeErrorCodes.NOT_ALLOWED)).and(new MessageMatcher("Job is not active")),
                () -> tokenApi.getToken("jobID", "token", true, null));
    }


    @Test
    public void testGetTokenReadTokenFail() throws ComputeException, WorkerManagerException, ExecutionException {
        MockitoAnnotations.openMocks(this);
        Mockito.doThrow(new ExecutionException(new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, "error"))).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class, new ExceptionCodeMatcher(ComputeErrorCodes.SERVICE_UNAVAILABLE),
                () -> tokenApi.getToken(
                        acquireTokenArguments.getJobId(), acquireTokenArguments.getJobSecret(), acquireTokenArguments.getRefresh(),
                        null)
        );
        verify(oxygenClient, times(0)).extendToken(anyString());
    }

    @Test
    public void testGetTokenUpdateTokenFail() throws ComputeException, WorkerManagerException, ExecutionException {
        MockitoAnnotations.openMocks(this);
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, "error")).when(dynamoDBJobClient).updateToken(anyString(), anyString());
        Mockito.doReturn(buildCompletedJobWithStatus(JobStatus.INPROGRESS)).when(jobCache).get(anyString());
        Mockito.doReturn("token").when(oxygenClient).extendToken(anyString());
        validateThrows(WorkerManagerException.class, new ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> tokenApi.getToken(
                        acquireTokenArguments.getJobId(), acquireTokenArguments.getJobSecret(), true,
                        null)
        );
        verify(oxygenClient, times(1)).extendToken(anyString());
    }

    @Test
    public void testAcquireTokenOk() throws ComputeException, WorkerManagerException, ExecutionException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn(buildCompletedJobWithStatus(JobStatus.INPROGRESS)).when(jobCache).get(anyString());
        Mockito.doReturn("token").when(oxygenClient).extendToken(anyString());
        assertEquals(200, tokenApi.acquireToken(acquireTokenArguments, null).getStatus());
        verify(oxygenClient, times(1)).extendToken(anyString());
        verify(dynamoDBJobClient, times(1)).updateToken(anyString(), anyString());
    }

    @Test
    public void testAcquireTokenOkNullRefresh() throws ComputeException, WorkerManagerException, ExecutionException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn(buildCompletedJobWithStatus(JobStatus.INPROGRESS)).when(jobCache).get(anyString());
        assertEquals(200, tokenApi.acquireToken(nullRefresh, null).getStatus());
        verify(oxygenClient, times(0)).extendToken(anyString());
    }

    // this is an issue. readToken returns null which should be handled properly in our code, not in oxygen
    @Test
    public void testAcquireTokenReadTokenFail() throws ComputeException, WorkerManagerException, ExecutionException {
        MockitoAnnotations.openMocks(this);
        Mockito.doThrow(new ExecutionException(
                "During get", new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, "error"))).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class, new ExceptionCodeMatcher(ComputeErrorCodes.SERVICE_UNAVAILABLE),
                () -> tokenApi.acquireToken(acquireTokenArguments, null)
        );
        verify(oxygenClient, times(0)).extendToken(anyString());
    }

    @Test
    public void testAcquireTokenOnlyExtendTokenFail() throws ComputeException, WorkerManagerException, ExecutionException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn(buildCompletedJobWithStatus(JobStatus.INPROGRESS)).when(jobCache).get(anyString());
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.OXYGEN_ERROR, "error")).when(oxygenClient).extendToken(anyString());
        validateThrows(WorkerManagerException.class, new ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_ERROR),
                () -> tokenApi.acquireToken(acquireTokenArguments, null)
        );
    }

    @Test
    public void testAcquireTokenUpdateTokenFail() throws ComputeException, WorkerManagerException, ExecutionException {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn(buildCompletedJobWithStatus(JobStatus.INPROGRESS)).when(jobCache).get(anyString());
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, "error")).when(dynamoDBJobClient).updateToken(anyString(), anyString());
        Mockito.doReturn(acquireTokenArguments.getJobSecret()).when(oxygenClient).extendToken(anyString());
        validateThrows(WorkerManagerException.class, new ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> tokenApi.acquireToken(acquireTokenArguments, null)
        );
        verify(oxygenClient, times(1)).extendToken(anyString());
    }

    @Test
    public void testAcquireTokenWorkerJobNotFound() throws ComputeException, WorkerManagerException {
        MockitoAnnotations.openMocks(this);
        final String exceptionMessage = makeString("Compute Job ", acquireTokenArguments.getJobId(), " does not exist, or has expired.");
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.NOT_FOUND, exceptionMessage)).when(dynamoDBJobClient).getJob(anyString());
        assertEquals(404, tokenApi.acquireToken(acquireTokenArguments, null).getStatus());
    }

    private static JobDBRecord buildCompletedJobWithStatus(final JobStatus jobStatus) {
        // build a job record
        final JobDBRecord jobRecord = JobDBRecord.createDefault(JOB_ID);
        jobRecord.setStatus(jobStatus.toString());
        jobRecord.setTags(new ArrayList<>());
        jobRecord.setPercentComplete(100);
        jobRecord.setBinaryPayload(compressString(JOB_PAYLOAD));
        jobRecord.setBinaryDetails(compressString(JOB_DETAILS));
        jobRecord.setOxygenToken("oxygenToken");
        jobRecord.setHeartbeatStatus(DynamoDBJobClient.HeartbeatStatus.ENABLED.toString());
        jobRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        jobRecord.setHeartbeatTimeoutSeconds(120);
        jobRecord.setCurrentRetry(0);
        jobRecord.setLastHeartbeatTime(0L);
        jobRecord.setCreationTime(JobDBRecord.fillCurrentTimeMilliseconds());
        jobRecord.setService("service");
        jobRecord.setWorker("worker");
        jobRecord.setJobFinishedTime(0L);
        jobRecord.setIdempotencyId("idempotencyId");
        jobRecord.setTags(JOB_TAGS);
        jobRecord.setErrors(JOB_ERRORS);
        return jobRecord;
    }
}
