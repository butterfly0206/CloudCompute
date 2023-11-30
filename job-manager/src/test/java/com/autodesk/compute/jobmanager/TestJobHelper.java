package com.autodesk.compute.jobmanager;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.batch.AWSBatch;
import com.amazonaws.services.batch.model.*;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.*;
import com.autodesk.compute.common.ComputeBatchDriver;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.Status;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.discovery.ResourceManager;
import com.autodesk.compute.discovery.ServiceResources;
import com.autodesk.compute.discovery.WorkerResolver;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.jobmanager.util.JobHelper;
import com.autodesk.compute.jobmanager.util.JobManagerException;
import com.autodesk.compute.jobmanager.util.JobStoreHelper;
import com.autodesk.compute.jobmanager.util.PollingJobHelper;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.cosv2.*;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.model.dynamodb.SearchJobsResult;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.util.AssumeRoleHelper;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;
import org.mockito.stubbing.Answer;

import jakarta.ws.rs.NotFoundException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.common.ServiceWithVersion.makeKey;
import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Category(UnitTests.class)
@Slf4j
public class TestJobHelper {

    private static final String DEFAULT = "DEFAULT";
    private static final JobDBRecord testJobDBRecord = JobDBRecord.createDefault(TestCommon.JOBID);
    private static final StartExecutionResult startExecutionResult = new StartExecutionResult();
    private static final SubmitJobResult submitJobResult = new SubmitJobResult().withJobName("jobName").withJobId("job-id");
    private static final ComputeSpecification computeSpec =
            new ComputeSpecification("worker", null, null, null, 1, 1, 100, null, null, false,null);
    private static final BatchJobDefinition batchJobDefinitionMaybePayload = BatchJobDefinition.builder()
            .jobDefinitionName("batchName")
            .computeSpecification(computeSpec)
            .jobAttempts(1)
            .batchJobOptions(BatchJobDefinition.BatchJobOptions.builder().neverSetJobPayload(false).build())
            .build();
    private static final BatchJobDefinition batchJobDefinitionNoPayload = BatchJobDefinition.builder()
            .jobDefinitionName("batchName")
            .computeSpecification(computeSpec)
            .jobAttempts(1)
            .batchJobOptions(BatchJobDefinition.BatchJobOptions.builder().neverSetJobPayload(true).build())
            .build();
    private static final BatchJobDefinition batchJobDefinitionNoOptions = BatchJobDefinition.builder()
            .jobDefinitionName("batchName")
            .computeSpecification(computeSpec)
            .jobAttempts(1)
            .build();

    private static final ServiceResources.BatchWorkerResources deployedBatchWorker = new ServiceResources.BatchWorkerResources(
            batchJobDefinitionMaybePayload, "jobName", "jobName", "appName", "deployed", ServiceResources.ServiceDeployment.DEPLOYED, Collections.emptyList());
    private static final ServiceResources.BatchWorkerResources testBatchWorker = new ServiceResources.BatchWorkerResources(
            batchJobDefinitionNoPayload, "jobName", "jobName", "appName", "test", ServiceResources.ServiceDeployment.TEST, Collections.emptyList());
    private static final ServiceResources.BatchWorkerResources testBatchWorkerNoOptions = new ServiceResources.BatchWorkerResources(
            batchJobDefinitionNoOptions, "jobName", "jobName", "appName", "test", ServiceResources.ServiceDeployment.TEST, Collections.emptyList());

    private static final ComponentDefinition componentDefinition = ComponentDefinition.builder()
            .componentName("ecsName")
            .computeSpecification(computeSpec)
            .build();
    private static final ServiceResources.ComponentWorkerResources deployedEcsWorker = new ServiceResources.ComponentWorkerResources(
            componentDefinition, "jobName", "jobName", "deployed", ServiceResources.ServiceDeployment.DEPLOYED, Collections.emptyList());

    private static final ServiceResources.ComponentWorkerResources testEcsWorker = new ServiceResources.ComponentWorkerResources(
            componentDefinition, "jobName", "jobName", "test", ServiceResources.ServiceDeployment.TEST, Collections.emptyList());

    // cross account batch worker
    private static final NameValueDefinition<String> crossAccountBatchQueue = new NameValueDefinition<>(DEFAULT, "clientBatchQueue");
    private static final ComputeSpecification crossAccountComputeSpec =
            new ComputeSpecification("worker", null, null, null, 1, 1, 100,
                    null, null, false, new ArrayList<>(Arrays.asList(crossAccountBatchQueue)));

    private static final BatchJobDefinition crossAccountBatchJobDefinition = BatchJobDefinition.builder()
            .jobDefinitionName("crossAccountBatchName")
            .computeSpecification(crossAccountComputeSpec)
            .jobAttempts(1)
            .containerProperties(null)
            .build();

    private static final DeploymentDefinition crossAccountDeployment = DeploymentDefinition.builder().accountId("clientId").region("clientRegion").build();
    private static final ServiceResources.BatchWorkerResources crossAccountBatchWorker = new ServiceResources.BatchWorkerResources(
            crossAccountBatchJobDefinition, "clientJobName", "clientJobName", "appName", "deployed",
            ServiceResources.ServiceDeployment.DEPLOYED, new ArrayList<>(Arrays.asList(crossAccountDeployment)));


    @Mock
    private AWSStepFunctions sfn;

    @Mock
    JobStoreHelper jobStore;

    @Mock
    private AWSBatch awsBatch;

    @Mock
    private AWSSecurityTokenService sts;

    @Mock
    private Metrics metrics;

    @Mock
    private WorkerResolver workerResolver;

    @Mock
    private ResourceManager resourceManager;

    @Mock
    private AmazonSQS sqs;

    @Spy
    @InjectMocks
    PollingJobHelper polling;

    @Mock
    private ComputeBatchDriver computeBatchDriver;

    private static final AssumeRoleResult assumeRoleResponse = new AssumeRoleResult();

    private JobHelper jobHelper;

    private static JobDBRecord buildJobRecord() {
        // build a job record
        final JobDBRecord jobRecord = JobDBRecord.createDefault("job00");
        jobRecord.setStatus(JobStatus.SCHEDULED.toString());
        jobRecord.setTags(Arrays.asList("test"));
        jobRecord.setPercentComplete(0);
        jobRecord.setBinaryPayload(ComputeStringOps.compressString("{}"));
        jobRecord.setOxygenToken("oxygenToken");
        jobRecord.setHeartbeatStatus(DynamoDBJobClient.HeartbeatStatus.ENABLED.toString());
        jobRecord.setHeartbeatTimeoutSeconds(120);
        jobRecord.setCurrentRetry(0);
        jobRecord.setLastHeartbeatTime(0L);
        jobRecord.setCreationTime(JobDBRecord.fillCurrentTimeMilliseconds());
        jobRecord.setService(TestCommon.SERVICE);
        jobRecord.setPortfolioVersion(TestCommon.PORTFOLIO_VERSION);
        jobRecord.setWorker(TestCommon.WORKER);
        jobRecord.setJobFinishedTime(0L);
        jobRecord.setIdempotencyId("idempotencyId");
        return jobRecord;
    }

    private static JobDBRecord buildBatchJobRecord() {
        final JobDBRecord jobRecord = buildJobRecord();
        jobRecord.setBatch(true);
        return jobRecord;
    }

    private static List<JobDBRecord> buildArrayBatchJobRecords() {
        final List<JobDBRecord> jobRecords = new ArrayList<>();
        for (int i = 0; i < TestCommon.ARRAY_JOB_SIZE; i++) {
            final String jobId = makeString(TestCommon.ARRAY_JOB_ID, ":", i);
            final JobDBRecord jobRecord = buildJobRecord();
            jobRecord.setJobId(jobId);
            jobRecord.setBatch(true);
            jobRecords.add(jobRecord);
        }
        return jobRecords;
    }

    private static List<JobDBRecord> buildArrayECSJobRecords() {
        final List<JobDBRecord> jobRecords = new ArrayList<>();
        for (int i = 0; i < TestCommon.ARRAY_JOB_SIZE; i++) {
            final String jobId = makeString(TestCommon.ARRAY_JOB_ID, ":", i);
            final JobDBRecord jobRecord = buildJobRecord();
            jobRecord.setJobId(jobId);
            jobRecords.add(jobRecord);
        }
        return jobRecords;
    }

    private static JobDBRecord buildEcsJobRecord() {
        final JobDBRecord jobRecord = buildJobRecord();
        jobRecord.setBatch(false);
        return jobRecord;
    }

    @BeforeClass
    public static void beforeClass() {
        startExecutionResult.setExecutionArn("jobarn");
    }

    @Before
    public void beforeTest() {
        MockitoAnnotations.openMocks(this);
        jobHelper = spy(new JobHelper(sfn, jobStore, polling));
        doReturn(resourceManager).when(workerResolver).getResourceManager();
        doReturn(workerResolver).when(jobHelper).getWorkerResolver();
        doReturn(metrics).when(jobHelper).getMetrics();
        doReturn(awsBatch).when(jobHelper).getBatchClient();
    }

    // This test also makes sure that when scheduling Batch job we schedule one sfn execution and batch submit is called once
    @Test
    public void testScheduleBatchJobOk() throws ComputeException, JobManagerException {
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class))).thenReturn(Optional.of(deployedBatchWorker));
        final JobDBRecord jobDBRecord = buildBatchJobRecord();
        jobDBRecord.setStatus(JobStatus.QUEUED.toString());
        Mockito.doNothing().when(jobStore).markJobScheduled(any(JobDBRecord.class));
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        jobHelper.scheduleJob(loader, jobDBRecord, null);

        // Validate sfn.startExecution is never called
        verify(sfn, never()).startExecution(any(StartExecutionRequest.class));

        // Make sure awsBatch.submitJob is is called exactly once
        verify(awsBatch, times(1)).submitJob(any(SubmitJobRequest.class));
    }

    // This test also makes sure that when scheduling an array batch job we schedule one sfn execution and batch submit is called once
    @Test
    public void testScheduleArrayBatchJobOk() throws ComputeException, JobManagerException {
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class))).thenReturn(Optional.of(deployedBatchWorker));
        final List<JobDBRecord> jobRecords = buildArrayBatchJobRecords();
        Mockito.doNothing().when(jobStore).markJobScheduled(any(JobDBRecord.class));
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        jobHelper.scheduleArrayJob(loader, TestCommon.ARRAY_JOB_ID, jobRecords, TestCommon.SERVICE, TestCommon.WORKER, deployedBatchWorker);

        // Validate sfn.startExecution is called exactly once
        verify(sfn, never()).startExecution(any(StartExecutionRequest.class));

        // Make sure awsBatch.submitJob is is called exactly once
        verify(awsBatch, times(1)).submitJob(any(SubmitJobRequest.class));
    }

    @Test
    public void testScheduleCrossAccountBatchJobOk() throws ComputeException, JobManagerException {

        try (final MockedStatic<AssumeRoleHelper> assumeRoleHelper = Mockito.mockStatic(AssumeRoleHelper.class)) {
            when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class))).thenReturn(Optional.of(crossAccountBatchWorker));
            final JobDBRecord jobDBRecord = buildBatchJobRecord();
            jobDBRecord.setStatus(JobStatus.QUEUED.toString());
            Mockito.doNothing().when(jobStore).markJobScheduled(any(JobDBRecord.class));
            Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
            Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
            final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

            final AWSCredentialsProvider awsCredentialsProvider = Mockito.spy(new STSAssumeRoleSessionCredentialsProvider.Builder(
                    String.format("arn:aws:iam::%s:role/%s-cross-account-execution-role", "accountId", "appName"), UUID.randomUUID().toString()).withStsClient(sts).build());

            final AWSSessionCredentials mockCredentials = Mockito.mock(AWSSessionCredentials.class);
            Mockito.doReturn(mockCredentials).when(awsCredentialsProvider).getCredentials();

            assumeRoleHelper.when(() -> AssumeRoleHelper.getCrossAccountCredentialsProvider("clientId", "clientRegion", "fpccomp-c-uw2-sb", "appName")).thenReturn(awsCredentialsProvider);
            Mockito.doReturn(assumeRoleResponse).when(sts).assumeRole(any(AssumeRoleRequest.class));
            jobHelper.scheduleJob(loader, jobDBRecord, null);

            // Validate sfn.startExecution is never called
            verify(sfn, never()).startExecution(any(StartExecutionRequest.class));

            // Make sure awsBatch.submitJob is is called exactly once
            verify(awsBatch, times(1)).submitJob(any(SubmitJobRequest.class));
        }
    }

    @Test
    public void testScheduleArrayBatchJobAWSThrottlingException() throws ComputeException {
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class))).thenReturn(Optional.of(deployedBatchWorker));
        final List<JobDBRecord> jobRecords = buildArrayBatchJobRecords();
        Mockito.doNothing().when(jobStore).markJobScheduled(any(JobDBRecord.class));
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");
        Mockito.doThrow(new AmazonClientException(ThrottleCheck.THROTTLING_STRINGS[1])).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> jobHelper.scheduleArrayJob(loader, TestCommon.ARRAY_JOB_ID, jobRecords, TestCommon.SERVICE, TestCommon.WORKER, deployedBatchWorker));
    }

    @Test
    public void testScheduleArrayBatchJobExceptionCoverage() throws ComputeException {
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class))).thenReturn(Optional.of(deployedBatchWorker));
        final List<JobDBRecord> jobRecords = buildArrayBatchJobRecords();
        Mockito.doNothing().when(jobStore).markJobScheduled(any(JobDBRecord.class));
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");
        Mockito.doThrow(new NotFoundException("Ambiguous not found exception")).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER),
                () -> jobHelper.scheduleArrayJob(loader, TestCommon.ARRAY_JOB_ID, jobRecords, TestCommon.SERVICE, TestCommon.WORKER, deployedBatchWorker));
    }

    // This test also makes sure that when scheduling ECS job we do not create a AWS batch job.
    @Test
    public void testScheduleECSJobOk() throws ComputeException, JobManagerException {
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class))).thenReturn(Optional.of(deployedEcsWorker));
        final JobDBRecord jobDBRecord = buildEcsJobRecord();
        jobDBRecord.setStatus(JobStatus.QUEUED.toString());
        Mockito.doNothing().when(jobStore).markJobScheduled(any(JobDBRecord.class));
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        jobHelper.scheduleJob(loader, jobDBRecord, null);

        // Validate sfn.startExecution is called exactly once
        verify(sfn, times(1)).startExecution(any(StartExecutionRequest.class));

        // Make sure awsBatch.submitJob is never called for ECS worker
        verify(awsBatch, never()).submitJob(any(SubmitJobRequest.class));
    }


    // This test also makes sure that when scheduling ECS job we do not create a AWS batch job
    // and ARRAY_JOB_SIZE times sfn executions
    @Test
    public void testScheduleArrayECSJobOk() throws ComputeException, JobManagerException {
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class))).thenReturn(Optional.of(testEcsWorker));
        final List<JobDBRecord> jobRecords = buildArrayECSJobRecords();
        Mockito.doNothing().when(jobStore).markJobScheduled(any(JobDBRecord.class));
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        jobHelper.scheduleArrayJob(loader, TestCommon.ARRAY_JOB_ID, jobRecords, TestCommon.SERVICE, TestCommon.WORKER, testEcsWorker);

        // Validate sfn.startExecution is called ARRAY_JOB_SIZE times
        verify(sfn, times(TestCommon.ARRAY_JOB_SIZE)).startExecution(any(StartExecutionRequest.class));

        // Make sure awsBatch.submitJob is never called for ECS worker
        verify(awsBatch, never()).submitJob(any(SubmitJobRequest.class));
    }

    @Test
    public void testScheduleArrayECSJobSFNThrottlingException() throws ComputeException {
        when(workerResolver.getWorkerResources(anyString(), anyString(), nullable(String.class))).thenReturn(Optional.of(deployedBatchWorker));
        final List<JobDBRecord> jobRecords = buildArrayECSJobRecords();
        Mockito.doNothing().when(jobStore).markJobScheduled(any(JobDBRecord.class));
        Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        Mockito.doThrow(new AmazonClientException(ThrottleCheck.THROTTLING_STRINGS[1])).when(sfn).startExecution(any(StartExecutionRequest.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> jobHelper.scheduleArrayJob(loader, TestCommon.ARRAY_JOB_ID, jobRecords, TestCommon.SERVICE, TestCommon.WORKER, deployedBatchWorker));
    }

    private JobDBRecord initExceptionTest() throws ComputeException {
        when(workerResolver.getWorkerResources(any(), any(), any())).thenReturn(Optional.of(deployedBatchWorker));
        final JobDBRecord jobDBRecord = buildBatchJobRecord();
        jobDBRecord.setStatus(JobStatus.QUEUED.toString());
        Mockito.doReturn(startExecutionResult).when(sfn).startExecution(any(StartExecutionRequest.class));
        Mockito.doReturn(submitJobResult).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        return jobDBRecord;
    }

    @Test
    public void testScheduleJobDynamoDBTransactionConflictException() throws ComputeException {
        final JobDBRecord jobDBRecord = initExceptionTest();
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        Mockito.doThrow(new ComputeException(ComputeErrorCodes.DYNAMODB_TRANSACTION_CONFLICT, "")).when(jobStore).markJobScheduled(any(JobDBRecord.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.DYNAMODB_TRANSACTION_CONFLICT),
                () -> jobHelper.scheduleJob(loader, jobDBRecord, null));
    }

    @Test
    public void testScheduleJobDynamoDBInternalException() throws ComputeException {
        final JobDBRecord jobDBRecord = initExceptionTest();
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        // internal exception while scheduling job
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, "")).when(jobStore).markJobScheduled(any(JobDBRecord.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER),
                () -> jobHelper.scheduleJob(loader, jobDBRecord, null));
    }

    @Test
    public void testScheduleJobComputeThrottlingException() throws ComputeException {
        final JobDBRecord jobDBRecord = initExceptionTest();
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.TOO_MANY_REQUESTS, "Throughput exceeds the current capacity")).when(jobStore).markJobScheduled(any(JobDBRecord.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> jobHelper.scheduleJob(loader, jobDBRecord, null));
    }

    @Test
    public void testScheduleJobDynamoDBadArnException() throws ComputeException {
        final JobDBRecord jobDBRecord = initExceptionTest();
        jobDBRecord.setBatch(false);
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        // Failed to store step ARN
        Mockito.doNothing().when(jobStore).markJobScheduled(any(JobDBRecord.class));
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN, "")).when(jobStore).addStepExecutionArn(any(), any());
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN),
                () -> jobHelper.scheduleJob(loader, jobDBRecord, null));
    }

    @Test
    public void testScheduleJobDynamoDBSetBatchIdException() throws ComputeException {
        final JobDBRecord jobDBRecord = initExceptionTest();
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        // Failed to set batch jobId
        Mockito.doNothing().when(jobStore).markJobScheduled(any(JobDBRecord.class));
        Mockito.doThrow(new ComputeException(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN, "")).when(jobStore).setBatchJobId(any(), any());
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN),
                () -> jobHelper.scheduleJob(loader, jobDBRecord, null));
    }

    @Test
    public void testScheduleJobBatchClientException() throws ComputeException {
        final JobDBRecord jobDBRecord = initExceptionTest();
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        // ClientException by batch
        Mockito.doThrow(new ClientException("")).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_BAD_REQUEST),
                () -> jobHelper.scheduleJob(loader, jobDBRecord, null));
    }

    @Test
    public void testScheduleJobBatchAWSBatchException() throws ComputeException {
        final JobDBRecord jobDBRecord = initExceptionTest();
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        // AWSBatchException by batch
        Mockito.doThrow(new AWSBatchException("")).when(awsBatch).submitJob(any(SubmitJobRequest.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> jobHelper.scheduleJob(loader, jobDBRecord, null));

    }

    @Test
    public void testScheduleJobSFNExecutionLimitExceededException() throws ComputeException {
        final JobDBRecord jobDBRecord = initExceptionTest();
        jobDBRecord.setBatch(false);
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        Mockito.doThrow(new ExecutionLimitExceededException("")).when(sfn).startExecution(any(StartExecutionRequest.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.RESOURCE_CREATION_ERROR),
                () -> jobHelper.scheduleJob(loader, jobDBRecord, null));
    }

    @Test
    public void testScheduleJobSFNStateMachineDeletingException() throws ComputeException {
        final JobDBRecord jobDBRecord = initExceptionTest();
        jobDBRecord.setBatch(false);
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        Mockito.doThrow(new StateMachineDeletingException("")).when(sfn).startExecution(any(StartExecutionRequest.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.RESOURCE_CREATION_ERROR),
                () -> jobHelper.scheduleJob(loader, jobDBRecord, null));
    }

    @Test
    public void testScheduleJobSFNAWSStepFunctionsException() throws ComputeException {
        final JobDBRecord jobDBRecord = initExceptionTest();
        jobDBRecord.setBatch(false);
        final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "scheduleJob");

        Mockito.doThrow(new AWSStepFunctionsException("")).when(sfn).startExecution(any(StartExecutionRequest.class));
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_BAD_REQUEST),
                () -> jobHelper.scheduleJob(loader, jobDBRecord, null));
    }

    private void initGetWorkerResourcesTest() throws ComputeException {
        final Map<String, ServiceResources.WorkerResources> replies = ImmutableMap.<String, ServiceResources.WorkerResources>builder()
                .put("deployed", deployedBatchWorker) // Deployed worker
                .put("2.0", testBatchWorker)          // Test Worker
                .put("1.0", deployedBatchWorker).build(); // Current worker (deployed)
        Mockito.when(workerResolver.getWorkerResources(anyString(), anyString(), anyString()))
                .thenAnswer((Answer) invocation -> {
                    final Object[] args = invocation.getArguments();
                    return Optional.of(replies.getOrDefault(args[2], deployedBatchWorker));
                });
    }

    @Test
    public void testGetDeployedWorkerResourcesOK() throws ComputeException, JobManagerException {
        initGetWorkerResourcesTest();
        final JobDBRecord jobDBRecord = buildBatchJobRecord();
        // Test with deployed portfolio version
        jobDBRecord.setPortfolioVersion("deployed");
        final ServiceResources.WorkerResources workerResources = jobHelper.getWorkerResources(jobDBRecord);
        Assert.assertEquals(deployedBatchWorker, workerResources);
    }

    @Test
    public void testGetTestWorkerResourcesOK() throws ComputeException, JobManagerException {
        initGetWorkerResourcesTest();
        final JobDBRecord jobDBRecord = buildBatchJobRecord();
        // Test with deployed portfolio version
        jobDBRecord.setPortfolioVersion("2.0");
        final ServiceResources.WorkerResources workerResources = jobHelper.getWorkerResources(jobDBRecord);
        Assert.assertEquals(testBatchWorker, workerResources);
    }

    @Test
    public void testGetCurrentWorkerResourcesOK() throws ComputeException, JobManagerException {
        initGetWorkerResourcesTest();
        final JobDBRecord jobDBRecord = buildBatchJobRecord();
        // Test with current portfolio version
        jobDBRecord.setPortfolioVersion("1.0");
        final ServiceResources.WorkerResources workerResources = jobHelper.getWorkerResources(jobDBRecord);
        Assert.assertEquals(deployedBatchWorker, workerResources);
    }

    @Test
    public void testGetWorkerResourcesInvalidServiceException() throws ComputeException {
        when(workerResolver.getWorkerResources(any(), any(), any())).thenReturn(Optional.empty());
        final JobDBRecord jobDBRecord = buildBatchJobRecord();

        // Test invalid service
        when(workerResolver.hasService(jobDBRecord.getService())).thenReturn(false);
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.INVALID_SERVICE_NAME),
                () -> jobHelper.getWorkerResources(jobDBRecord));
    }

    @Test
    public void testGetWorkerResourcesInvalidPortfolioException() throws ComputeException {
        when(workerResolver.getWorkerResources(any(), any(), any())).thenReturn(Optional.empty());
        final JobDBRecord jobDBRecord = buildBatchJobRecord();

        // Test invalid portfolio
        // resourceManager.hasService returns
        // true whe called with jobDBRecord.getService()
        // false with called with jobDBRecord.getService() and jobDBRecord.getResolvedPortfolioVersion()
        final Map<String, Boolean> replies2 = ImmutableMap.<String, Boolean>builder()
                .put(jobDBRecord.getService(), true)
                .put(makeKey(jobDBRecord.getService(), jobDBRecord.getResolvedPortfolioVersion()), false).build();
        Mockito.when(workerResolver.hasService(anyString()))
                .thenAnswer((Answer) invocation -> {
                    final Object[] args = invocation.getArguments();
                    return replies2.getOrDefault(args[0], false);
                });
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.INVALID_PORTFOLIO_VERSION),
                () -> jobHelper.getWorkerResources(jobDBRecord));
    }

    @Test
    public void testGetWorkerResourcesInvalidWorkerException() throws ComputeException {
        when(workerResolver.getWorkerResources(any(), any(), any())).thenReturn(Optional.empty());
        final JobDBRecord jobDBRecord = buildBatchJobRecord();

        // Test unknown worker
        // resourceManager.hasService returns
        // true whe called with jobDBRecord.getService()
        // true with called with jobDBRecord.getService() and jobDBRecord.getResolvedPortfolioVersion()
        final Map<String, Boolean> replies3 = ImmutableMap.<String, Boolean>builder()
                .put(jobDBRecord.getService(), true)
                .put(makeKey(jobDBRecord.getService(), jobDBRecord.getResolvedPortfolioVersion()), true).build();
        Mockito.when(workerResolver.hasService(anyString()))
                .thenAnswer((Answer) invocation -> {
                    final Object[] args = invocation.getArguments();
                    return replies3.getOrDefault(args[0], false);
                });
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.INVALID_WORKER_NAME),
                () -> jobHelper.getWorkerResources(jobDBRecord));
    }

    @Test
    public void testSendQueueIdToSQSOk() {
        doReturn(sqs).when(jobHelper).getAmazonSQS();
        jobHelper.sendQueueIdToSQS("queu-id");
        // Validate sqs.sendMessage is called exactly once
        verify(sqs, times(1)).sendMessage(nullable(SendMessageRequest.class));
    }

    private String makePayload(final int length) {
        return String.format("{ \"payload\": \"%s\" }", RandomStringUtils.randomAlphanumeric(length));

    }

    @Test
    public void testBatchEnvironmentPayload() throws JobManagerException {
        List<KeyValuePair> environment = jobHelper.gatherServiceIntegrationEnvironment(
                "a-job-id", "a-service", "a-worker",
                makePayload(24), Collections.emptyList(), testBatchWorker);
        Assert.assertFalse(environment.stream().anyMatch(oneItem -> "COS_JOB_PAYLOAD".equals(oneItem.getName())));

        environment = jobHelper.gatherServiceIntegrationEnvironment(
                "a-job-id", "a-service", "a-worker",
                makePayload(24), Collections.emptyList(), deployedBatchWorker);
        Assert.assertTrue(environment.stream().anyMatch(oneItem -> "COS_JOB_PAYLOAD".equals(oneItem.getName())));

        environment = jobHelper.gatherServiceIntegrationEnvironment(
                "a-job-id", "a-service", "a-worker",
                makePayload(6000), Collections.emptyList(), deployedBatchWorker);
        Assert.assertFalse(environment.stream().anyMatch(oneItem -> "COS_JOB_PAYLOAD".equals(oneItem.getName())));

        environment = jobHelper.gatherServiceIntegrationEnvironment(
                "a-job-id", "a-service", "a-worker",
                makePayload(128), Collections.emptyList(), testBatchWorkerNoOptions);
        Assert.assertTrue(environment.stream().anyMatch(oneItem -> "COS_JOB_PAYLOAD".equals(oneItem.getName())));

        environment = jobHelper.gatherServiceIntegrationEnvironment(
                "a-job-id", "a-service", "a-worker",
                makePayload(6200), Collections.emptyList(), testBatchWorkerNoOptions);
        Assert.assertFalse(environment.stream().anyMatch(oneItem -> "COS_JOB_PAYLOAD".equals(oneItem.getName())));

    }

    @Test
    public void testCancelJobsOk() throws ComputeException {
        final SearchJobsResult arrayJobResult = new SearchJobsResult(TestCommon.buildArrayJobRecords(), null);

        when(jobHelper.getComputeBatchDriver()).thenReturn(computeBatchDriver);

        Mockito.doReturn(arrayJobResult).when(jobStore).searchArrayJob(anyString(), nullable(String.class));
        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(jobStore).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(new StopExecutionResult()).when(sfn).stopExecution((any(StopExecutionRequest.class)));

        jobHelper.cancelJobs(TestCommon.ARRAY_JOB_ID, "ARRAY_BATCH_JOB_ID");

        // verify we array batch job has been canceled.
        verify(computeBatchDriver, times(1)).verifyBatchJobTerminated(anyString(), anyString());

        verify(jobStore, times(TestCommon.ARRAY_JOB_SIZE)).markJobCanceled(any(JobDBRecord.class));
        // We do not stop sfn execution for batch jobs.
        verify(sfn, never()).stopExecution((any(StopExecutionRequest.class)));
    }

    @Test
    public void testCancelJobTwiceException() throws JobManagerException, ComputeException, ExecutionException {
        testJobDBRecord.setJobId("a-batch-job");
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");
        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(metrics).reportJobCancelFailed(anyString(), anyString());
        Mockito.doReturn(new StopExecutionResult()).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doNothing().when(jobStore).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(new StopExecutionResult()).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobStore).getJobFromCache(anyString(), anyBoolean());
        jobHelper.cancelJob(testJobDBRecord);
        testJobDBRecord.setStatus(Status.CANCELED.toString());
        validateThrows(JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.BAD_REQUEST),
                () -> jobHelper.cancelJob(testJobDBRecord));
    }

    @Test(expected=JobManagerException.class)
    public void testCancelJobThrottlingException() throws JobManagerException, ComputeException, ExecutionException {
        testJobDBRecord.setJobId(TestCommon.JOBID);
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");

        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(jobStore).markJobCanceled(any(JobDBRecord.class));
        final AmazonClientException e = new AmazonClientException(ThrottleCheck.THROTTLING_STRINGS[1]);
        Mockito.doThrow(e).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobStore).getJobFromCache(anyString(), anyBoolean());
        jobHelper.cancelJob(testJobDBRecord); // Should throw
        fail("Delete job should have thrown, but did not");
    }

    @Test
    public void testCancelJobAlreadyCompleted() throws JobManagerException, ComputeException {
        testJobDBRecord.setStatus(Status.COMPLETED.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");
        Mockito.doNothing().when(metrics).reportJobCanceled(anyString(), anyString());
        Mockito.doNothing().when(jobStore).markJobCanceled(any(JobDBRecord.class));
        Mockito.doReturn(new StopExecutionResult()).when(sfn).stopExecution((any(StopExecutionRequest.class)));
        Mockito.doReturn(testJobDBRecord).when(jobStore).getJobFromCache(anyString(), anyBoolean());
        validateThrows(
                JobManagerException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.BAD_REQUEST),
                () -> jobHelper.cancelJob(testJobDBRecord)
        );
    }

    @Test
    public void testCancelJobFailedToUpdate() throws ComputeException, JobManagerException {
        final ConditionalCheckFailedException ccfe = new ConditionalCheckFailedException("test");
        final String error = makeString("Can't perform update on job id ", TestCommon.JOBID);
        final ComputeException ce = new ComputeException(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN, error, ccfe);
        Mockito.doThrow(ce).when(jobStore).markJobCanceled(any(JobDBRecord.class));
        testJobDBRecord.setStatus(Status.INPROGRESS.toString());
        testJobDBRecord.setBatch(false);
        testJobDBRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        testJobDBRecord.setService("a-test-service");
        testJobDBRecord.setWorker("testworker");
        Mockito.doReturn(testJobDBRecord).when(jobStore).getJobFromCache(anyString(), anyBoolean());
        Mockito.doNothing().when(jobStore).removeJobFromQueue(any(JobDBRecord.class));
        validateThrows(
                JobManagerException.class,
                new Matchers.MessageMatcher(error),
                () -> jobHelper.cancelJob(testJobDBRecord)
        );
    }
}
