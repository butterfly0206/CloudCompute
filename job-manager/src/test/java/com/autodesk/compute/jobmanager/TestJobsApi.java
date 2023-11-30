package com.autodesk.compute.jobmanager;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.batch.AWSBatch;
import com.amazonaws.services.batch.model.SubmitJobRequest;
import com.amazonaws.services.batch.model.SubmitJobResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.*;
import com.autodesk.compute.auth.oauth.OxygenSecurityContext;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.OAuthTokenUtils;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.*;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.discovery.ServiceResources;
import com.autodesk.compute.discovery.WorkerResolver;
import com.autodesk.compute.dynamodb.*;
import com.autodesk.compute.jobmanager.api.NotFoundException;
import com.autodesk.compute.jobmanager.api.impl.JobsApiServiceImpl;
import com.autodesk.compute.jobmanager.util.JobHelper;
import com.autodesk.compute.jobmanager.util.JobManagerException;
import com.autodesk.compute.jobmanager.util.JobStoreHelper;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.OAuthToken;
import com.autodesk.compute.model.cosv2.BatchJobDefinition;
import com.autodesk.compute.model.cosv2.ComponentDefinition;
import com.autodesk.compute.model.cosv2.ComputeSpecification;
import com.autodesk.compute.model.cosv2.NameValueDefinition;
import com.autodesk.compute.model.cosv2.DeploymentDefinition;
import com.autodesk.compute.model.dynamodb.IdempotentJobDBRecord;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.model.dynamodb.QueueInfo;
import com.autodesk.compute.model.dynamodb.SearchJobsResult;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.categories.UnitTests;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Category(UnitTests.class)
@Slf4j
public class TestJobsApi {

    private static final String DEFAULT = "DEFAULT";

    private static final MultivaluedMap<String, String> requestHeader = initRequestHeaders();

    private static final JobDBRecord testJobDBRecord = JobDBRecord.createDefault(TestCommon.JOBID);
    private static final ComputeSpecification computeSpec =
            new ComputeSpecification(TestCommon.WORKER, null, null, null, 1, 1, 100, null, null, false, null);
    private static final List<NameValueDefinition<Integer>> jobConcurrencyLimits = Arrays.asList(new NameValueDefinition<>("Student", 2));
    private static final ComputeSpecification computeSpecConcurrent =
            new ComputeSpecification(TestCommon.WORKER, null, null, null, 1, 1, 100, null, jobConcurrencyLimits, false, null);
    private static final String TEST_GATEWAY_SECRET_V1 = "testGatewaySecretV1";

    private static JobArgs testJobArgs = new JobArgs();

    private static ArrayJobArgs testArrayJobArgs = new ArrayJobArgs();

    private static final BatchJobDefinition batchJobDefinition = BatchJobDefinition.builder()
            .jobDefinitionName("jobName")
            .computeSpecification(computeSpec)
            .jobAttempts(1)
            .containerProperties(null)
            .build();
    private static final ServiceResources.BatchWorkerResources batchWorker = new ServiceResources.BatchWorkerResources(
            batchJobDefinition, "jobName", "jobName", "appName", "deployed", ServiceResources.ServiceDeployment.DEPLOYED, null);


    private static final ComponentDefinition componentDefinition = ComponentDefinition.builder()
            .computeSpecification(computeSpec)
            .build();

    private static final ServiceResources.ComponentWorkerResources ecsWorker = new ServiceResources.ComponentWorkerResources(
            componentDefinition, "jobName", "jobName", "deployed", ServiceResources.ServiceDeployment.DEPLOYED, null);


    private static final SubmitJobResult submitJobResult = new SubmitJobResult().withJobName("jobName").withJobId("job-id");
    private static final StartExecutionResult startExecutionResult = new StartExecutionResult();
    private static final StopExecutionResult stopExecutionResult = new StopExecutionResult();
    private static final OxygenSecurityContext oxygenContext =
            new OxygenSecurityContext(ImmutableSet.of("testGatewaySecretV2", "testGatewaySecretV1"),
                    true, getOAuthToken());
    private static final IdempotentJobDBRecord idempotentJobDBRecord = new IdempotentJobDBRecord();

    // cross account batch worker
    private static final NameValueDefinition<String> crossAccountBatchQueue =new NameValueDefinition<>(DEFAULT, "clientBatchQueue");
    private static final ComputeSpecification crossAccountComputeSpec =
            new ComputeSpecification("worker", null, null, null, 1, 1, 100,
                    null, null, false,new ArrayList<NameValueDefinition<String>>(Arrays.asList(crossAccountBatchQueue)));

    private static final BatchJobDefinition crossAccountBatchJobDefinition = BatchJobDefinition.builder()
            .jobDefinitionName("crossAccountBatchName")
            .computeSpecification(crossAccountComputeSpec)
            .jobAttempts(1)
            .containerProperties(null)
            .build();

    private static final DeploymentDefinition crossAccountDeployment = DeploymentDefinition.builder().accountId("clientId").region("clientRegion").build();
    private static final ServiceResources.BatchWorkerResources crossAccountBatchWorker = new ServiceResources.BatchWorkerResources(
            crossAccountBatchJobDefinition, "clientJobName", "clientJobName", "appName", "deployed",
            ServiceResources.ServiceDeployment.DEPLOYED, new ArrayList<com.autodesk.compute.model.cosv2.DeploymentDefinition>(Arrays.asList(crossAccountDeployment)));


    @Mock
    private AWSStepFunctions sfn;

    @Mock
    private DynamoDBJobClient dynamoDBJobClient;

    @Mock
    private DynamoDBIdempotentJobsClient dynamoDBIdempotentJobsClient;

    @Mock
    private DynamoDBActiveJobsCountClient dynamoDBActiveJobsCountClient;

    @Mock
    private AWSBatch awsBatch;

    @Mock
    private ComputeConfig computeConfig;

    @Mock
    private ServiceResources.WorkerResources workerResource;

    @Mock
    private WorkerResolver workerResolver;

    @Mock
    private Metrics metrics;

    @Mock
    private JobHelper jobHelper;

    @Spy
    @InjectMocks
    JobStoreHelper jobStore;

    @Mock
    private AmazonSQS sqs;

    @Mock
    LoadingCache<String, JobDBRecord> jobCache = JobCache.makeJobLoadingCache(300, 10, dynamoDBJobClient);

    JobsApiServiceImpl jobsApiService;

    private static MultivaluedMap<String, String> initRequestHeaders() {
        final MultivaluedMap<String, String> result = new MultivaluedHashMap<>();
        result.put("x-ads-token-data", Arrays.asList("{\"scope\":\"read\",\"expires_in\":3600,\"client_id\":\"test_client\",\"access_token\":{\"client_id\":\"test_client\",\"userid\":\"test_userid\",\"username\":\"test_username\",\"email\":\"test_email\",\"lastname\":\"test_lastname\",\"firstname\":\"test_firstname\",\"entitlements\":\"\"}}"));
        result.put("x-ads-appid", Arrays.asList("testAppId"));
        result.put("Authorization", Arrays.asList("Bearer someOxygenToken"));
        result.put("x-ads-gateway-secret", Arrays.asList(TEST_GATEWAY_SECRET_V1));
        return result;
    }

    private static OAuthToken getOAuthToken() {
        try {
            return OAuthTokenUtils.from(requestHeader);
        } catch (final IOException e) {
            return null;
        }
    }

    private void initialize() {
        MockitoAnnotations.openMocks(this);
        this.jobStore = Mockito.spy(new JobStoreHelper(
                dynamoDBJobClient,
                dynamoDBIdempotentJobsClient,
                dynamoDBActiveJobsCountClient,
                metrics,
                jobCache));
        this.jobsApiService = Mockito.spy(new JobsApiServiceImpl(jobStore, jobHelper, workerResolver));
        testJobArgs = TestCommon.getJobArgs();
        testArrayJobArgs = TestCommon.getArrayJobArgs();
    }

    @BeforeClass
    public static void beforeClass() {
        startExecutionResult.setExecutionArn("jobarn");
    }

    @Before
    public void beforeTest() {
        initialize();
    }

    @Test
    public void testJobStoreHelperConstructorCoverage() {
        final JobStoreHelper impl = new JobStoreHelper();
    }

    @Test
    public void testCreateBatchJobOk() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        Mockito.doNothing().when(metrics).reportJobSubmitted(anyString(), anyString());
        Mockito.doReturn(
                Optional.of((ServiceResources.WorkerResources) batchWorker))
                .when(workerResolver)
                .getWorkerResources(anyString(), anyString(), nullable(String.class));

        Mockito.doReturn(TestCommon.buildJobRecord()).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        final Response response = jobsApiService.createJob(testJobArgs, false, oxygenContext);
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntity());
        assertEquals(Job.class, response.getEntity().getClass());
        final Job job = (Job) response.getEntity();

        // Validate we are getting non empty status update
        assertNotNull(job.getStatusUpdates());
        assertNotEquals(0, job.getStatusUpdates());
    }

    @Test
    public void testCreateCrossAccountBatchJobOk() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        Mockito.doNothing().when(metrics).reportJobSubmitted(anyString(), anyString());
        Mockito.doReturn(
                Optional.of((ServiceResources.WorkerResources) crossAccountBatchWorker))
                .when(workerResolver)
                .getWorkerResources(anyString(), anyString(), nullable(String.class));

        Mockito.doReturn(TestCommon.buildJobRecord()).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        final Response response = jobsApiService.createJob(testJobArgs, false, oxygenContext);
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntity());
        assertEquals(Job.class, response.getEntity().getClass());
        final Job job = (Job) response.getEntity();

        // Validate we are getting non empty status update
        assertNotNull(job.getStatusUpdates());
        assertNotEquals(0, job.getStatusUpdates());
    }

    @Test
    public void testCreateArrayBatchJobOk() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        Mockito.doNothing().when(metrics).reportJobSubmitted(anyString(), anyString());
        Mockito.doReturn(
                Optional.of((ServiceResources.WorkerResources) batchWorker))
                .when(workerResolver)
                .getWorkerResources(anyString(), anyString(), nullable(String.class));

        Mockito.doReturn(TestCommon.buildArrayJobRecords()).when(dynamoDBJobClient).insertJobs(any(List.class));
        final Response response = jobsApiService.createJobs(testArrayJobArgs, oxygenContext);
        assertEquals(202, response.getStatus());
        assertNotNull(response.getEntity());
        assertEquals(ArrayJob.class, response.getEntity().getClass());
        final ArrayJob job = (ArrayJob) response.getEntity();

        verify(dynamoDBJobClient, times(1)).insertJobs(any(List.class));

        // Validate we created ARRAY_JOB_SIZE number of jobs
        assertNotNull(job.getJobID());
        assertEquals(testArrayJobArgs.getService(), job.getService());
        assertEquals(testArrayJobArgs.getWorker(), job.getWorker());
        assertNotNull(job.getPortfolioVersion());
        assertEquals(TestCommon.ARRAY_JOB_SIZE, job.getJobs().size());
    }

    @Test
    public void testCreateArrayEcsJobOk() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        //Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        Mockito.doNothing().when(metrics).reportJobSubmitted(anyString(), anyString());
        Mockito.doReturn(
                Optional.of((ServiceResources.WorkerResources) ecsWorker))
                .when(workerResolver)
                .getWorkerResources(anyString(), anyString(), nullable(String.class));

        Mockito.doReturn(TestCommon.buildArrayJobRecords()).when(dynamoDBJobClient).insertJobs(any(List.class));
        final Response response = jobsApiService.createJobs(testArrayJobArgs, oxygenContext);
        assertEquals(202, response.getStatus());
        assertNotNull(response.getEntity());
        assertEquals(ArrayJob.class, response.getEntity().getClass());
        final ArrayJob job = (ArrayJob) response.getEntity();

        verify(dynamoDBJobClient, times(1)).insertJobs(any(List.class));

        // Validate we created ARRAY_JOB_SIZE number of jobs
        assertNotNull(job.getJobID());
        assertEquals(testArrayJobArgs.getService(), job.getService());
        assertEquals(testArrayJobArgs.getWorker(), job.getWorker());
        assertNotNull(job.getPortfolioVersion());
        assertEquals(TestCommon.ARRAY_JOB_SIZE, job.getJobs().size());
    }

    @Test
    public void testCreateJobAsynchronousOk() throws JobManagerException, ComputeException {
        when(jobsApiService.getComputeConfig()).thenReturn(computeConfig);
        when(computeConfig.createJobsAsynchronously(anyString())).thenReturn(true);
        when(sfn.startExecution(any(StartExecutionRequest.class))).thenReturn(startExecutionResult);
        when(awsBatch.submitJob(any(SubmitJobRequest.class))).thenReturn(submitJobResult);
        Mockito.doNothing().when(metrics).reportJobSubmitted(anyString(), anyString());
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(batchWorker));


        when(dynamoDBJobClient.insertJob(any(JobDBRecord.class))).thenReturn(TestCommon.buildJobRecord());
        assertEquals(Response.Status.ACCEPTED.getStatusCode(), jobsApiService.createJob(testJobArgs, false, oxygenContext).getStatus());
    }

    @Test
    public void testCreateJobAsynchronousWithExclusionOk() throws JobManagerException, ComputeException {
        when(jobsApiService.getComputeConfig()).thenReturn(computeConfig);
        when(computeConfig.createJobsAsynchronously(anyString())).thenReturn(false);
        when(sfn.startExecution(any(StartExecutionRequest.class))).thenReturn(startExecutionResult);
        when(awsBatch.submitJob(any(SubmitJobRequest.class))).thenReturn(submitJobResult);
        Mockito.doNothing().when(metrics).reportJobSubmitted(anyString(), anyString());
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(batchWorker));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(batchWorker));

        when(dynamoDBJobClient.insertJob(any(JobDBRecord.class))).thenReturn(TestCommon.buildJobRecord());
        assertEquals(Response.Status.OK.getStatusCode(), jobsApiService.createJob(testJobArgs, false, oxygenContext).getStatus());
    }

    @Test
    public void testCreateBatchJobDynamoThrows() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        Mockito.doReturn(
                Optional.of(batchWorker))
                .when(workerResolver)
                .getWorkerResources(anyString(), anyString(), nullable(String.class));
        Mockito.doReturn(
                Optional.of((ServiceResources.WorkerResources) batchWorker))
                .when(workerResolver)
                .getWorkerResources(anyString(), anyString(), nullable(String.class));
        final ComputeException e = new ComputeException(ComputeErrorCodes.AWS_BAD_REQUEST, new NullPointerException());
        Mockito.doThrow(e).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_BAD_REQUEST),
                () -> jobsApiService.createJob(testJobArgs, false, oxygenContext));
    }

    @Test
    public void testCreateBatchJobBadWorker() throws ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        Mockito.doReturn(
                Optional.empty())
                .when(workerResolver)
                .getWorkerResources(anyString(), anyString(), nullable(String.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.INVALID_JOB_PAYLOAD),
                () -> jobsApiService.createJob(testJobArgs, false, oxygenContext));
    }


    @Test
    public void testCreateBatchJobStoreGetJobCountThrottling() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        final JobDBRecord jobRecord = JobDBRecord.createDefault("job1");
        jobRecord.setStatus(JobStatus.QUEUED.toString());
        jobRecord.setUserType("Student");
        jobRecord.setService(TestCommon.SERVICE);
        jobRecord.setWorker(TestCommon.WORKER);
        final QueueInfo queueInfo = new QueueInfo();
        queueInfo.setVersion(0);
        jobRecord.setQueueInfo(queueInfo);
        // We're doing the concurrent workflow to exercise the tryScheduleOrQueue codepath for coverage
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).enqueueJob(anyString(), anyString());
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).peekJob(anyString());
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).removeJobFromQueue(any(JobDBRecord.class));
        Mockito.doReturn(computeSpecConcurrent).when(workerResource).getComputeSpecification();
        Mockito.doReturn(ServiceResources.ServiceDeployment.DEPLOYED).when(workerResource).getDeploymentType();
        testJobArgs.setUserType("Student");
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity")).when(jobStore).getActiveJobsCount(anyString());
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> jobsApiService.createJob(testJobArgs, false, oxygenContext));
    }


    @Test
    public void testCreateBatchJobStoreGetJobCountExceptionCoverage() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        final JobDBRecord jobRecord = JobDBRecord.createDefault("job1");
        jobRecord.setStatus(JobStatus.QUEUED.toString());
        jobRecord.setUserType("Student");
        jobRecord.setService(TestCommon.SERVICE);
        jobRecord.setWorker(TestCommon.WORKER);
        final QueueInfo queueInfo = new QueueInfo();
        queueInfo.setVersion(0);
        jobRecord.setQueueInfo(queueInfo);
        // We're doing the concurrent workflow to exercise the tryScheduleOrQueue codepath for coverage
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).enqueueJob(anyString(), anyString());
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).peekJob(anyString());
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).removeJobFromQueue(any(JobDBRecord.class));
        Mockito.doReturn(computeSpecConcurrent).when(workerResource).getComputeSpecification();
        Mockito.doReturn(ServiceResources.ServiceDeployment.DEPLOYED).when(workerResource).getDeploymentType();
        testJobArgs.setUserType("Student");
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.SERVER, "Unknown ambiguous server error")).when(jobStore).getActiveJobsCount(anyString());
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_CONFLICT),
                () -> jobsApiService.createJob(testJobArgs, false, oxygenContext));
    }


    @Test
    public void testCreateBatchJobStoreRemoveJobExceptionMasked() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        final JobDBRecord jobRecord = JobDBRecord.createDefault("job1");
        jobRecord.setStatus(JobStatus.QUEUED.toString());
        jobRecord.setUserType("Student");
        jobRecord.setService(TestCommon.SERVICE);
        jobRecord.setWorker(TestCommon.WORKER);
        final QueueInfo queueInfo = new QueueInfo();
        queueInfo.setVersion(0);
        jobRecord.setQueueInfo(queueInfo);
        // We're doing the concurrent workflow to exercise the tryScheduleOrQueue codepath for coverage
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).enqueueJob(anyString(), anyString());
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).peekJob(anyString());
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).removeJobFromQueue(any(JobDBRecord.class));
        Mockito.doReturn(computeSpecConcurrent).when(workerResource).getComputeSpecification();
        Mockito.doReturn(ServiceResources.ServiceDeployment.DEPLOYED).when(workerResource).getDeploymentType();
        testJobArgs.setUserType("Student");
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.SERVER, "Unknown ambiguous server error")).when(jobStore).removeJobFromQueue(any(JobDBRecord.class));
        assertEquals(Response.Status.OK.getStatusCode(), jobsApiService.createJob(testJobArgs, false, oxygenContext).getStatus());
    }


    @Test
    public void testCreateEcsJobOk() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        Mockito.doReturn(TestCommon.buildJobRecord()).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();

        assertEquals(200, jobsApiService.createJob(testJobArgs, false, oxygenContext).getStatus());
    }

    @Test
    public void testCreateEcsIdempotentJobOk() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        Mockito.doReturn(TestCommon.buildJobRecord()).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        Mockito.doReturn(null).when(dynamoDBIdempotentJobsClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        testJobArgs.setIdempotencyId("idempotency-id");
        assertEquals(200, jobsApiService.createJob(testJobArgs, false, oxygenContext).getStatus());
    }

    @Test
    public void testCreateEcsIdempotentJobTwiceFail() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        Mockito.doReturn(TestCommon.buildJobRecord()).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        Mockito.doReturn(null).when(dynamoDBIdempotentJobsClient).getJob(anyString());
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        testJobArgs.setIdempotencyId("idempotency-id");
        assertEquals(200, jobsApiService.createJob(testJobArgs, false, oxygenContext).getStatus());

        // Create the job with same IdempotencyId and it should fail.
        Mockito.doReturn(idempotentJobDBRecord).when(dynamoDBIdempotentJobsClient).getJob(anyString());

        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.IDEMPOTENT_JOB_EXISTS),
                () -> jobsApiService.createJob(testJobArgs, false, oxygenContext));
    }

    @Test
    public void testCreateJobMissingTokenException() throws JobManagerException, ComputeException {
        Mockito.doReturn(workerResource).when(jobsApiService).getWorkerResources(anyString(), anyString(), anyString(), anyBoolean());
        Mockito.doReturn(TestCommon.buildJobRecord()).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_MISSING_TOKEN_ERROR),
                () -> jobsApiService.createJob(testJobArgs, false, null));
    }

    @Test
    public void testCreateJobWrongWorkerException() throws JobManagerException, ComputeException {
        final JobManagerException e = new JobManagerException(ComputeErrorCodes.INVALID_JOB_PAYLOAD, "");
        Mockito.doThrow(e).when(jobsApiService).getWorkerResources(anyString(), anyString(), anyString(), anyBoolean());
        Mockito.doReturn(TestCommon.buildJobRecord()).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.INVALID_JOB_PAYLOAD),
                () -> jobsApiService.createJob(testJobArgs, false, oxygenContext));
    }

    @Test
    public void testCreateJobThrottleExceptionJobHelper() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        Mockito.doReturn(TestCommon.buildJobRecord()).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doAnswer(invocation -> {
            throw new JobManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity");
        }).when(jobHelper).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), any(ServiceResources.WorkerResources.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> jobsApiService.createJob(testJobArgs, false, oxygenContext));
    }

    @Test
    public void testDeleteJobOk() throws JobManagerException, ComputeException, ExecutionException {
        testJobDBRecord.setJobId(TestCommon.JOBID);
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");

        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(stopExecutionResult).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        assertEquals(200, jobsApiService.deleteJob(TestCommon.JOBID, null).getStatus());
    }

    @Test
    public void testDeleteJobMissingExecutionArnOk() throws JobManagerException, ComputeException, ExecutionException {
        testJobDBRecord.setJobId(TestCommon.JOBID);
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");

        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doThrow(new ExecutionDoesNotExistException("From test")).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        assertEquals(200, jobsApiService.deleteJob(TestCommon.JOBID, null).getStatus());
    }

    @Test
    public void testThrottlingThrows() throws JobManagerException, ComputeException {
        final Exception e = new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, new AmazonClientException(ThrottleCheck.THROTTLING_STRINGS[0]));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        Mockito.doReturn(computeSpec).when(workerResource).getComputeSpecification();
        Mockito.doThrow(e).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        validateThrows(
                JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> jobsApiService.createJob(testJobArgs, Boolean.TRUE, oxygenContext)
        );
    }

    @Test
    public void testDeleteJobScheduleOk() throws JobManagerException, ComputeException, ExecutionException {
        testJobDBRecord.setStatus(Status.SCHEDULED.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");
        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(stopExecutionResult).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        assertEquals(200, jobsApiService.deleteJob(TestCommon.JOBID, null).getStatus());
    }

    @Test
    public void testDeleteArrayJobOk() throws JobManagerException, ComputeException {
        final SearchJobsResult arrayJobResult = new SearchJobsResult(TestCommon.buildArrayJobRecords(), null);

        Mockito.doReturn(arrayJobResult.getJobs().get(0)).when(jobStore).getJobFromCache(nullable(String.class), anyBoolean());
        Mockito.doReturn(arrayJobResult).when(dynamoDBJobClient).getArrayJobs(anyString(), nullable(String.class));
        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(jobStore).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(stopExecutionResult).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        assertEquals(202, jobsApiService.deleteJob(TestCommon.ARRAY_JOB_ID, null).getStatus());
    }

    @Test
    public void testDeleteArnJobOk() throws JobManagerException, ComputeException, ExecutionException {
        testJobDBRecord.setJobId("a-batch-job");
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");
        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(stopExecutionResult).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        assertEquals(200, jobsApiService.deleteJob(TestCommon.JOBID, null).getStatus());
    }

    @Test
    public void testDeleteInvalidJobException() throws ComputeException, ExecutionException {
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(null).when(jobCache).get(anyString());
        Mockito.doNothing().when(metrics).reportJobCancelFailed(anyString(), anyString());
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_FOUND),
                () -> jobsApiService.deleteJob(TestCommon.JOBID, null));
    }

    @Test
    public void testGetJobOk() throws JobManagerException, ExecutionException {
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        assertEquals(200, jobsApiService.getJob(TestCommon.JOBID, null, null).getStatus());
    }

    @Test
    public void testGetArrayJobOk() throws JobManagerException, ComputeException, ExecutionException {

        Mockito.doReturn(TestCommon.buildSearchJobResult()).when(dynamoDBJobClient).getArrayJobs(anyString(), nullable(String.class));
        final Response response = jobsApiService.getJob(TestCommon.ARRAY_JOB_ID, null, null);
        assertEquals(200, response.getStatus());
        assertNotNull(response.getEntity());
        assertEquals(ArrayJobResult.class, response.getEntity().getClass());
        final ArrayJobResult arrayJob = (ArrayJobResult) response.getEntity();

        // Validate we created ARRAY_JOB_SIZE number of jobs
        assertNotNull(arrayJob.getJobID());
        assertEquals(TestCommon.SERVICE, arrayJob.getService());
        assertEquals(TestCommon.WORKER, arrayJob.getWorker());
        assertEquals(TestCommon.PORTFOLIO_VERSION, arrayJob.getPortfolioVersion());
        assertEquals(TestCommon.ARRAY_JOB_SIZE, arrayJob.getJobs().size());

        // Validate that each jobs in the array job have unique ids by adding them to a set and validating against array size.
        assertEquals(TestCommon.ARRAY_JOB_SIZE, arrayJob.getJobs().stream().map(JobInfo::getJobID).collect(Collectors.toSet()).size());

        verify(dynamoDBJobClient, atMostOnce()).getArrayJobs(anyString(), nullable(String.class));

        // Also make sure we never touched the jobCache
        verify(jobCache, never()).get(anyString());
    }

    // Make sure that a child job in an array is just treated as a regular job (not an array job that returns a list of jobs)
    @Test
    public void testGetAChildArrayJobOk() throws JobManagerException, ExecutionException, ComputeException {
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        final String aChildJobId = makeString(TestCommon.ARRAY_JOB_ID, ":", 0);
        assertEquals(200, jobsApiService.getJob(aChildJobId, null, null).getStatus());

        verify(jobCache, atLeastOnce()).get(anyString());
        verify(dynamoDBJobClient, never()).getArrayJobs(anyString(), nullable(String.class));
    }

    @Test
    public void testGetInvalidJobException() throws ExecutionException {
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        Mockito.doReturn(null).when(jobCache).get(anyString());
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_FOUND),
                () -> jobsApiService.getJob(TestCommon.JOBID, null, null));
    }

    @Test
    public void testGetNonExistingArrayJob() throws ExecutionException, ComputeException {
        Mockito.doReturn(null).when(dynamoDBJobClient).getArrayJobs(anyString(), nullable(String.class));

        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_FOUND),
                () -> jobsApiService.getJob(TestCommon.ARRAY_JOB_ID, null, null));

        verify(jobCache, never()).get(anyString());
        verify(dynamoDBJobClient, atLeastOnce()).getArrayJobs(anyString(), nullable(String.class));
    }

    @Test
    public void testErrorProcessing() throws JobManagerException, ExecutionException, JsonProcessingException {
        testJobDBRecord.setStatus(Status.FAILED.toString());
        final Failure jobFailure = new Failure();
        jobFailure.setError("Nothing");
        testJobDBRecord.setErrors(
                Lists.newArrayList(Json.mapper.writeValueAsString(jobFailure))
        );
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        final Response failedJobResponse = jobsApiService.getJob("aaaaaaaa-bbbb-cccc-dddd-fedcbafedbca", null, null);

        final String entityBody = Json.mapper.writeValueAsString(failedJobResponse.getEntity());
        final JsonNode parsedObject = Json.mapper.readTree(entityBody);
        final JsonNode errors = parsedObject.get("errors");
        assertNotNull("There should have been errors in " + entityBody, errors);
        assertEquals("There should be exactly 1 error", 1, errors.size());
        final JsonNode oneError = errors.get(0);
        assertEquals("The error should have made it", "Nothing", oneError.get("error").asText());
        assertFalse("Null values shouldn't be serialized", oneError.has("details"));
    }


    @Test
    public void testCreateConcurrentJobOk() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        final JobDBRecord jobRecord = JobDBRecord.createDefault("job1");
        jobRecord.setStatus(JobStatus.QUEUED.toString());
        jobRecord.setUserType("Student");
        jobRecord.setService(TestCommon.SERVICE);
        jobRecord.setWorker(TestCommon.WORKER);
        final QueueInfo queueInfo = new QueueInfo();
        queueInfo.setVersion(0);
        jobRecord.setQueueInfo(queueInfo);

        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).enqueueJob(anyString(), anyString());
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).peekJob(anyString());
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).removeJobFromQueue(any(JobDBRecord.class));
        Mockito.doNothing().when(jobHelper).scheduleJob(
                any(MDCLoader.class), any(JobDBRecord.class), any(ServiceResources.WorkerResources.class));

        Mockito.doReturn(computeSpecConcurrent).when(workerResource).getComputeSpecification();
        Mockito.doReturn(ServiceResources.ServiceDeployment.DEPLOYED).when(workerResource).getDeploymentType();
        testJobArgs.setUserType("Student");

        Mockito.doReturn(0).when(dynamoDBActiveJobsCountClient).getCount(anyString());
        Response response = jobsApiService.createJob(testJobArgs, false, oxygenContext);
        assertEquals(200, response.getStatus());
        assertEquals(JobStatus.SCHEDULED, parseJobStatus(response));

        Mockito.doReturn(1).when(dynamoDBActiveJobsCountClient).getCount(anyString());
        response = jobsApiService.createJob(testJobArgs, false, oxygenContext);
        assertEquals(JobStatus.SCHEDULED, parseJobStatus(response));

        // Now we have reached concurrency limit of 2 next job should be in QUEUED state
        Mockito.doReturn(2).when(dynamoDBActiveJobsCountClient).getCount(anyString());
        response = jobsApiService.createJob(testJobArgs, false, oxygenContext);
        assertEquals(JobStatus.QUEUED, parseJobStatus(response));
    }

    /*
        Scenario on createJob when trying to schedule a queued job, DynamoDB transaction conflict
        happens due to ongoing transaction on the same items involved. Instead of returning 409 we
        internally trigger SQS message to schedule the job via JobScheduler simply return
        200 and job status as QUEUED.
     */
    @Test
    public void testDynamoDBConflictHandleOk() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        final JobDBRecord jobRecord = JobDBRecord.createDefault("job1");
        jobRecord.setStatus(JobStatus.QUEUED.toString());
        jobRecord.setUserType("Student");
        jobRecord.setService(TestCommon.SERVICE);
        jobRecord.setWorker(TestCommon.WORKER);
        final QueueInfo queueInfo = new QueueInfo();
        queueInfo.setVersion(0);
        jobRecord.setQueueInfo(queueInfo);

        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).enqueueJob(anyString(), anyString());
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).peekJob(anyString());
        Mockito.doReturn(jobRecord).when(dynamoDBJobClient).removeJobFromQueue(any(JobDBRecord.class));
        Mockito.doThrow(new JobManagerException(ComputeErrorCodes.DYNAMODB_TRANSACTION_CONFLICT, "")).when(jobHelper)
                .scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), any(ServiceResources.WorkerResources.class));

        Mockito.doReturn(computeSpecConcurrent).when(workerResource).getComputeSpecification();
        Mockito.doReturn(ServiceResources.ServiceDeployment.DEPLOYED).when(workerResource).getDeploymentType();
        testJobArgs.setUserType("Student");

        Mockito.doReturn(0).when(dynamoDBActiveJobsCountClient).getCount(anyString());
        Mockito.doReturn(new SendMessageResult()).when(sqs).sendMessage(any(SendMessageRequest.class));
        final Response response = jobsApiService.createJob(testJobArgs, false, oxygenContext);
        assertEquals(200, response.getStatus());
        assertEquals(JobStatus.QUEUED, parseJobStatus(response));
    }

    // TODO: This test doesn't fail. Why was there a caught exception here before?
    @Test
    public void testCreateConcurrentFail() throws JobManagerException, ComputeException {
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.of(workerResource));
        Mockito.doReturn(TestCommon.buildJobRecord()).when(dynamoDBJobClient).insertJob(any(JobDBRecord.class));

        Mockito.doReturn(computeSpecConcurrent).when(workerResource).getComputeSpecification();

        Mockito.doReturn(0).when(dynamoDBActiveJobsCountClient).getCount(anyString());
        final Response response = jobsApiService.createJob(testJobArgs, false, oxygenContext);
        Assert.assertNotNull(response);
        Assert.assertEquals(200, response.getStatus());
    }

    private JobStatus parseJobStatus(final Response response) {
        final ObjectMapper jsonMapper = new ObjectMapper();
        final MapType mapType = jsonMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);
        final LinkedHashMap<String, Object> entity = jsonMapper.convertValue(response.getEntity(), mapType);
        try {
            return JobStatus.valueOf((String) entity.get("status"));
        } catch (final Exception e) {
            fail("Exception not expected: " + e);
        }
        return null;
    }

    /* This test case deletes a scheduled jobs using an Oxygen Security Token
     * The jobs were not created using Oxygen token, hence should be NOT_AUTHORIZED.
     * */
    @Test
    public void testDeleteJobScheduleAuth() throws ComputeException, ExecutionException {
        testJobDBRecord.setStatus(Status.SCHEDULED.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");
        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(stopExecutionResult).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        validateThrows(
                JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_AUTHORIZED),
                () -> jobsApiService.deleteJob(TestCommon.JOBID, oxygenContext)
        );
    }

    /* This test case deletes an already completed jobs using an Oxygen Security Token
     * The jobs were not created using Oxygen token, hence should be NOT_AUTHORIZED.
     * */
    @Test
    public void testDeleteJobCompleteAuth() throws ComputeException, ExecutionException {
        testJobDBRecord.setStatus(Status.COMPLETED.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");
        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(stopExecutionResult).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        validateThrows(
                JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_AUTHORIZED),
                () -> jobsApiService.deleteJob(TestCommon.JOBID, oxygenContext)
        );
    }

    /* This test case deletes an already completed jobs using an Oxygen Security Token
     * The jobs were not created using Oxygen token, hence should be NOT_AUTHORIZED.
     * */
    @Test
    public void testDeleteJobWithUserID() throws ComputeException, ExecutionException {
        testJobDBRecord.setStatus(Status.COMPLETED.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");
        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(stopExecutionResult).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        validateThrows(
                JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_AUTHORIZED),
                () -> jobsApiService.deleteJob(TestCommon.JOBID, oxygenContext)
        );
    }

    @Test
    public void testDeleteJobNullValue() throws ComputeException, ExecutionException {
        testJobDBRecord.setJobId(TestCommon.JOBID);
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");

        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(stopExecutionResult).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(dynamoDBJobClient).getJob(anyString());
        validateThrows(
                JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.BAD_REQUEST),
                () -> jobsApiService.deleteJob(null, oxygenContext)
        );
    }

    @Test
    public void testAddTagOk() throws ComputeException, ExecutionException, NotFoundException {
        testJobDBRecord.setJobId(TestCommon.JOBID);
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");

        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(stopExecutionResult).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        assertFalse(testJobDBRecord.getTags().contains("test-tag"));
        assertEquals(200, jobsApiService.addTag(TestCommon.JOBID, "test-tag", null).getStatus());
        assertTrue(testJobDBRecord.getTags().contains("test-tag"));
        assertEquals(304, jobsApiService.addTag(TestCommon.JOBID, "test-tag", null).getStatus());
        validateThrows(
                JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_FOUND),
                () -> jobsApiService.addTag("non-existing-job-id", "test-tag", null)
        );

    }

    @Test
    public void testDeleteTagOk() throws ComputeException, ExecutionException, NotFoundException {
        testJobDBRecord.setJobId(TestCommon.JOBID);
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");
        testJobDBRecord.setTags(new ArrayList<>() {{
            add("test-tag");
        }});

        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(dynamoDBJobClient).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(stopExecutionResult).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobCache).get(anyString());
        assertTrue(testJobDBRecord.getTags().contains("test-tag"));
        assertEquals(200, jobsApiService.deleteTag(TestCommon.JOBID, "test-tag", null).getStatus());
        assertFalse(testJobDBRecord.getTags().contains("test-tag"));
        assertEquals(304, jobsApiService.deleteTag(TestCommon.JOBID, "test-tag", null).getStatus());
        validateThrows(
                JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_FOUND),
                () -> jobsApiService.deleteTag("non-existing-job-id", "test-tag", null)
        );
    }

    @Test
    public void testGetWorkerResourcesOk() throws JobManagerException, ComputeException {
        doReturn("test").when(workerResource).getPortfolioVersion();
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(workerResource));
        assertNotNull(jobsApiService.getWorkerResources("acmecomp-c-uw2", "sample", "test", false));
    }
}
