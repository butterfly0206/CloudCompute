package com.autodesk.compute.workermanager;

import com.autodesk.compute.auth.basic.BasicAuthSecurityContext;
import com.autodesk.compute.auth.basic.BasicCredentials;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.model.Failure;
import com.autodesk.compute.common.model.Status;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobCache;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.workermanager.api.NotFoundException;
import com.autodesk.compute.workermanager.api.impl.JobsApiServiceImpl;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.MockitoAnnotations.openMocks;

@Category(UnitTests.class)
@Slf4j
public class TestJobsApi {
    public static final String JOBID = "jobid";

    private static JobDBRecord testJob = new JobDBRecord();

    private final BasicAuthSecurityContext context = new BasicAuthSecurityContext(true,
            Optional.of(
                    new BasicCredentials("testService", "flintstone")));

    @Mock
    private DynamoDBJobClient dynamoDBJobClient;

    @Mock
    private final LoadingCache<String, JobDBRecord> jobCache = JobCache.makeJobLoadingCache(300, 10, dynamoDBJobClient);

    @Spy
    @InjectMocks
    private JobsApiServiceImpl jobsApiService;

    private static void initialize() {
        testJob = new JobDBRecord();
        testJob.setJobId("somejob");
        testJob.setWorker("worker");
        testJob.setService("service");
        testJob.setModificationTime(System.currentTimeMillis());
    }

    @Before
    public void beforeTest() {
        initialize();
    }

    @Test
    public void testJobsApiConstructorCoverage() {
        JobsApiServiceImpl impl = new JobsApiServiceImpl();
        impl = new JobsApiServiceImpl(jobCache);
    }

    @Test
    public void testGetJobOk() throws WorkerManagerException, NotFoundException, ExecutionException {
        openMocks(this);
        testJob.setStatus(Status.INPROGRESS.toString());
        Mockito.doReturn(testJob).when(jobCache).get(anyString());
        assertEquals(200, jobsApiService.getJob(JOBID, context).getStatus());
    }

    @Test
    public void testGetInvalidJobIdException() throws ExecutionException, NotFoundException {
        openMocks(this);
        testJob.setStatus(Status.INPROGRESS.toString());
        Mockito.doReturn(null).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.BAD_REQUEST),
                () -> jobsApiService.getJob(null, context));
    }

    @Test
    public void testGetInvalidJobException() throws ExecutionException, NotFoundException {
        openMocks(this);
        testJob.setStatus(Status.INPROGRESS.toString());
        Mockito.doReturn(null).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_FOUND),
                () -> jobsApiService.getJob(JOBID, context));
    }

    @Test
    public void testGetComputeExecutionException() throws ExecutionException, NotFoundException {
        openMocks(this);
        testJob.setStatus(Status.INPROGRESS.toString());
        Mockito.doThrow(new ExecutionException(new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, "error"))).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> jobsApiService.getJob(JOBID, context));
    }

    @Test
    public void testGetUnknownExecutionException() throws ExecutionException, NotFoundException {
        openMocks(this);
        testJob.setStatus(Status.INPROGRESS.toString());
        Mockito.doThrow(new ExecutionException(new RuntimeException("Ambiguous runtime error"))).when(jobCache).get(anyString());
        validateThrows(WorkerManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> jobsApiService.getJob(JOBID, context));
    }

    @Test
    public void testErrorProcessing() throws ExecutionException, NotFoundException, JsonProcessingException {
        openMocks(this);
        testJob.setStatus(Status.FAILED.toString());
        final Failure jobFailure = new Failure();
        jobFailure.setError("Nothing");
        testJob.setErrors(
                Lists.newArrayList(Json.mapper.writeValueAsString(jobFailure))
        );
        Mockito.doReturn(testJob).when(jobCache).get(anyString());
        final Response failedJobResponse = jobsApiService.getJob("my-job", context);
        final String entityBody = Json.mapper.writeValueAsString(failedJobResponse.getEntity());
        final JsonNode parsedObject = Json.mapper.readTree(entityBody);
        final JsonNode errors = parsedObject.get("errors");
        assertNotNull("There should have been errors in " + entityBody, errors);
        assertEquals("There should be exactly 1 error", 1, errors.size());
        final JsonNode oneError = errors.get(0);
        assertEquals("The error should have made it", "Nothing", oneError.get("error").asText());
        assertFalse("Null values shouldn't be serialized", oneError.has("details"));
    }

}
