package com.autodesk.compute.jobmanager.util;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.batch.AWSBatch;
import com.amazonaws.services.batch.AWSBatchClientBuilder;
import com.amazonaws.services.batch.model.*;
import com.amazonaws.services.securitytoken.model.AWSSecurityTokenServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;
import com.amazonaws.services.stepfunctions.model.*;
import com.amazonaws.util.StringUtils;
import com.autodesk.compute.common.ComputeBatchDriver;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.Failure;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeConstants;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.discovery.ServiceResources;
import com.autodesk.compute.discovery.WorkerResolver;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.BatchJobCreationArgs;
import com.autodesk.compute.model.cosv2.BatchJobDefinition;
import com.autodesk.compute.model.cosv2.ComputeSpecification;
import com.autodesk.compute.model.cosv2.DeploymentDefinition;
import com.autodesk.compute.model.cosv2.NameValueDefinition;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.model.dynamodb.SearchJobsResult;
import com.autodesk.compute.util.AssumeRoleHelper;
import com.autodesk.compute.util.JsonHelper;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.common.ComputeUtils.firstSupplier;
import static com.autodesk.compute.common.ServiceWithVersion.isCurrentVersion;
import static com.autodesk.compute.common.ServiceWithVersion.makeKey;
import static com.autodesk.compute.jobmanager.util.JobManagerException.wrapAndRethrow;
import static com.google.common.base.MoreObjects.firstNonNull;

@Slf4j
public class JobHelper {

    private final ComputeConfig computeConfig;
    private static final String NONE_JOB_ID = "";
    private static final String DEFAULT = "DEFAULT";

    private final ComputeSpecification defaultComputeSpecification = ComputeSpecification.ComputeSpecificationBuilder.buildWithDefaults();
    private final BatchJobDefinition.BatchJobOptions defaultBatchJobOptions =
            BatchJobDefinition.BatchJobOptions.builder().build();
    private final AWSStepFunctions sfn;
    private final AmazonSQS sqs;
    private final Metrics metrics;
    private final ComputeBatchDriver computeBatchDriver;
    private final JobStoreHelper jobStore;
    private final PollingJobHelper polling;

    @Getter
    private AWSBatch batchClient;

    private static final int MAX_ENVIRONMENT_VARIABLE_LENGTH = 5 * 1024;

    // public for mocking
    public ComputeBatchDriver getComputeBatchDriver() {
        return computeBatchDriver;
    }

    @Inject
    public JobHelper() {
        this(makeStandardClient(AWSStepFunctionsClientBuilder.standard()), new JobStoreHelper(), new PollingJobHelper());
    }

    public JobHelper(final AWSStepFunctions sfn, final JobStoreHelper jobStore, final PollingJobHelper polling) {
        this.computeConfig = ComputeConfig.getInstance();
        this.metrics = Metrics.getInstance();
        this.sfn = sfn;
        this.jobStore = jobStore;
        this.sqs = makeStandardClient(AmazonSQSClientBuilder.standard());
        this.computeBatchDriver = new ComputeBatchDriver( ComputeConfig.getInstance().getBatchTerminationOnCancelJob());
        this.polling = polling;
    }

    // For mocking
    public WorkerResolver getWorkerResolver() {
        return WorkerResolver.getInstance();
    }

    public Metrics getMetrics() {
        return Metrics.getInstance();
    }

    public AmazonSQS getAmazonSQS() {
        return sqs;
    }

    public void scheduleJob(final MDCLoader loader, @NonNull final JobDBRecord jobRecord, final ServiceResources.WorkerResources argWorkerResources)
            throws JobManagerException {
        final ServiceResources.WorkerResources workerResources = firstSupplier(
                () -> argWorkerResources, () -> getWorkerResources(jobRecord));

        if (JobStatus.QUEUED.toString().equals(jobRecord.getStatus())) {
            try {
                jobStore.markJobScheduled(jobRecord);
            } catch (final ComputeException e) {
                if (ThrottleCheck.isThrottlingException(e)) {
                    final String error = makeString("Failed to mark job to SCHEDULED due to throttling for jobID: ", jobRecord.getJobId());
                    safelyUpdateJobStatusToFailed(jobRecord, error);
                    throw new JobManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, e);
                } else if (e.getCode() == ComputeErrorCodes.DYNAMODB_TRANSACTION_CONFLICT) {
                    final String error = makeString("Failed to mark job to SCHEDULED due to transaction conflict in DynamoDB for jobID: ", jobRecord.getJobId());
                    safelyUpdateJobStatusToFailed(jobRecord, error);
                    throw new JobManagerException(ComputeErrorCodes.DYNAMODB_TRANSACTION_CONFLICT, error, e);
                } else {
                    final String error = makeString("Failed to mark job to SCHEDULED in DynamoDB for jobID: ", jobRecord.getJobId());
                    safelyUpdateJobStatusToFailed(jobRecord, error);
                    throw new JobManagerException(ComputeErrorCodes.SERVER, error, e);
                }
            }
        }

        final String batchJobId = validateBatchJobCreation(loader, jobRecord, workerResources);

        // Create a SFN execution
        // If this fails, we will not delete the job thrown in on AWS Batch pool.
        // Better to let one job timeout when no jobs are in the SFN queue
        createStepfunctionExecution(loader, jobRecord, workerResources);

        log.info("scheduleJob({}:{}) {} (batch {})", jobRecord.getService(), jobRecord.getWorker(), jobRecord.getJobId(), batchJobId);
    }

    public void scheduleArrayJob(final MDCLoader loader, final String arrayJobId, final List<JobDBRecord> jobRecords,
                                 final String service, final String worker,
                                 final ServiceResources.WorkerResources workerResources) throws JobManagerException {

        try {
            String batchJobId = NONE_JOB_ID;
            if (workerResources instanceof ServiceResources.BatchWorkerResources) {
                batchJobId = validateBatchJobCreation(loader,
                        new BatchJobCreationArgs(arrayJobId, jobRecords, service, worker, workerResources.getPortfolioVersion(),
                                null, null, null), (ServiceResources.BatchWorkerResources) workerResources);
            }
            // Create a SFN execution
            // If this fails, we will not delete the job thrown in on AWS Batch pool.
            // Better to let one job timeout when no jobs are in the SFN queue
            for (final JobDBRecord jobRecord : jobRecords) {
                createStepfunctionExecution(loader, jobRecord, workerResources);
            }
            log.info("scheduleArrayJob({}:{}) {} (batch {})", service, worker, arrayJobId, batchJobId);

        } catch (final Exception e) {
            // Check specifically for throttling exception types, wrap if so, rethrow otherwise
            if (ThrottleCheck.isThrottlingException(e)) {
                throw new JobManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, e);
            }
            throw new JobManagerException(ComputeErrorCodes.SERVER, e);
        }
    }

    private String getBatchJobQueueName(final String batchQueueType, final ComputeSpecification computeSpecification) {
        if (computeSpecification.getJobQueues() != null
                && !computeSpecification.getJobQueues().isEmpty()) {
            final Map<String, String> limits = computeSpecification.getJobQueues().stream()
                    .collect(Collectors.toMap(NameValueDefinition::getName, NameValueDefinition::getValue));

            if (batchQueueType != null && limits.containsKey(batchQueueType)) {
                return limits.get(batchQueueType);
            } else {
                return limits.get(DEFAULT);
            }
        }
        // if computeSpecification does not have jobQueues it probably a V2 job and we will return null to fallback to
        // V2 batch queue
        return null;
    }

    private String createBatchJob(final String jobId, final int arraySize, final String service, final String worker,
                                  final String payload, final List<String> tags, final String batchQueueType,
                                  final ServiceResources.BatchWorkerResources batchWorker) throws JobManagerException {
        log.info(makeString("createBatchJob called for jobId ", jobId));

        final String workerManagerUrl = ComputeConfig.isTestRequest() ? computeConfig.getTestWorkerManagerURL()
                : computeConfig.getWorkerManagerURL();

        final ContainerOverrides overrides = new ContainerOverrides()
                .withEnvironment(new KeyValuePair().withName("WORKER_MANAGER_URL").withValue(workerManagerUrl));

        final List<KeyValuePair> siEnvironment = gatherServiceIntegrationEnvironment(jobId, service, worker,
                payload, tags, batchWorker);
        siEnvironment.forEach(overrides::withEnvironment);

        final BatchJobDefinition batchJobDefinition = batchWorker.getJobDefinition();
        final ComputeSpecification computeSpecification = batchWorker.getComputeSpecification();
        final int jobAttempts = firstNonNull(batchJobDefinition.getJobAttempts(),
                firstNonNull(computeSpecification.getJobAttempts(), defaultComputeSpecification.getJobAttempts()));

        final int jobTimeoutSeconds = firstNonNull(computeSpecification.getJobTimeoutSeconds(),
                defaultComputeSpecification.getJobTimeoutSeconds());

        // check whether cross account batch worker explicitly defines queues, otherwise get after name convention
        final String batchJobQueue = firstNonNull(getBatchJobQueueName(batchQueueType, batchWorker.getComputeSpecification()), batchWorker.getBatchQueue());

        final SubmitJobRequest submitJobRequest = getSubmitBatchJobRequest(batchWorker, overrides, jobAttempts, jobTimeoutSeconds, batchJobQueue);

        // This is an array batch job
        if (arraySize > 1) {
            submitJobRequest.setArrayProperties(new ArrayProperties().withSize(arraySize));
        }

        try {
          log.info("Submitting batch job for job definition {} with worker {} to queue {}", batchWorker.getBatchDefinitionName(), worker, batchJobQueue);
          final SubmitJobResult submitJobResult = getBatchClient().submitJob(submitJobRequest);
          log.info("SubmitJobResult: {}", submitJobResult);
          return submitJobResult.getJobId();
        } catch (final ClientException e) {
            final ComputeErrorCodes code = ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_BAD_REQUEST;
            throw new JobManagerException(code, e);
        } catch (final AWSBatchException e) {
            final ComputeErrorCodes code = ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR;
            throw new JobManagerException(code, e);
        } catch (final Exception e) {
            throw new JobManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, e);
        }
    }

    private SubmitJobRequest getSubmitBatchJobRequest(final ServiceResources.BatchWorkerResources batchWorker, final ContainerOverrides overrides, final int jobAttempts, final int jobTimeoutSeconds, final String jobQueue) {

        final SubmitJobRequest submitJobRequest = new SubmitJobRequest()
                .withJobName(batchWorker.getBatchDefinitionName())
                .withContainerOverrides(overrides)
                .withJobDefinition(batchWorker.getBatchDefinitionName())
                .withRetryStrategy(new RetryStrategy().withAttempts(jobAttempts))
                .withJobQueue(jobQueue)
                .withTimeout(new JobTimeout().withAttemptDurationSeconds(jobTimeoutSeconds));

        // make either a standard batch client or a batch client that will assume the cross-account execution role
        final List<DeploymentDefinition> deployments = batchWorker.getDeployments();
        String executionAccountId = computeConfig.getAwsAccountId();
        String region = computeConfig.getRegion();
        if (!deployments.isEmpty()) {
            final DeploymentDefinition deployment = deployments.get(0);
            executionAccountId = deployment.getAccountId();
            region = deployment.getRegion();
        }

        if (executionAccountId.compareTo(computeConfig.getAwsAccountId()) == 0) {
            log.info("Creating standard batch client for account:{} region:{}", executionAccountId, region);
            batchClient = makeStandardClient(AWSBatchClientBuilder.standard());
        } else {
            // Note, there is only ever one deployment, despite it being stored in a list
            // if we ever get into a world where one moniker is deployed into multiple regions,
            // we'll have several things to do to enable it; we'll worry about that when it comes up.
            log.info("Creating cross account credentials provider for account:{} region:{} app:{}", executionAccountId, region, batchWorker.getAppName());
            AWSCredentialsProvider crossAccountCredentialsProvider = AssumeRoleHelper.getCrossAccountCredentialsProvider(executionAccountId, region, computeConfig.getAppMoniker(), batchWorker.getAppName());
            try {
                log.info("Creating batch client with assumeRole credentials for account:{} region:{} app:{}", executionAccountId, region, batchWorker.getAppName());
                batchClient = makeStandardClient(AWSBatchClientBuilder.standard(), crossAccountCredentialsProvider.getCredentials(), region);
            } catch (final AWSSecurityTokenServiceException e) {
                // fallback to v2 cross account assume role arn
                log.info("Creating cross account credentials fallback provider for account:{} region:{}", executionAccountId, region);
                crossAccountCredentialsProvider = AssumeRoleHelper.getCrossAccountCredentialsProvider(executionAccountId, region, computeConfig.getAppMoniker(), null);
                log.info("Creating batch client with assumeRole credentials for account:{} region:{}", executionAccountId, region);
                batchClient = makeStandardClient(AWSBatchClientBuilder.standard(), crossAccountCredentialsProvider.getCredentials(), region);
            }
            submitJobRequest.setRequestCredentialsProvider(crossAccountCredentialsProvider);
        }
        return submitJobRequest;
    }


    private String createBatchJobWithRetries(final String jobId, final int arraySize, final String service, final String worker,
                                             final String portfolioVersion, final String payload, final List<String> tags, final String batchQueueType,
                                             final ServiceResources.BatchWorkerResources batchWorker) throws JobManagerException {
        try {
            return createBatchJob(jobId, arraySize, service, worker, payload, tags, batchQueueType, batchWorker);
        } catch (final JobManagerException e) {
            if (ThrottleCheck.isJobDefinitionDoesNotExistException(e.getCause())) {
                // If the job doesn't exist, it's probably an invalid cache problem; try
                // invalidating it and checking again
                getWorkerResolver().invalidateResource(service);
                final ServiceResources.BatchWorkerResources refreshedBatchWorker = (ServiceResources.BatchWorkerResources)
                        getWorkerResources(service, worker, portfolioVersion);
                return createBatchJob(jobId, arraySize, service, worker, payload, tags, batchQueueType, refreshedBatchWorker);
            } else {
                throw e;
            }
        }
    }

    public ServiceResources.WorkerResources getWorkerResources(final JobDBRecord jobDBRecord) throws JobManagerException {
        return getWorkerResources(jobDBRecord.getService(), jobDBRecord.getWorker(), jobDBRecord.getResolvedPortfolioVersion());
    }

    public ServiceResources.WorkerResources getWorkerResources(final String service, final String worker, final String portfolioVersion) throws JobManagerException {
        Optional<ServiceResources.WorkerResources> testWorkerResources = Optional.empty();
        Optional<ServiceResources.WorkerResources> deployedWorkerResources = Optional.empty();
        Optional<ServiceResources.WorkerResources> resolvedWorkerResources = Optional.empty();

        final boolean currentlyDeployedWorker;
        try {
            // Figure out if the worker making the API call is the currently deployed
            // version. If it is, then give it a current version job. Otherwise give
            // it a test job.
            if (isCurrentVersion(portfolioVersion)) {
                deployedWorkerResources = getWorkerResolver().getWorkerResources(service, worker, portfolioVersion);
                currentlyDeployedWorker = true;
            } else {
                testWorkerResources = getWorkerResolver().getWorkerResources(service, worker, portfolioVersion);
                deployedWorkerResources = getWorkerResolver().getWorkerResources(service, worker, null);
                currentlyDeployedWorker = deployedWorkerResources
                        .map(workerResources -> workerResources.getPortfolioVersion().equals(portfolioVersion))
                        .orElse(false);
            }

        } catch (final ComputeException e) {
            throw new JobManagerException(e);
        }

        resolvedWorkerResources = currentlyDeployedWorker ? deployedWorkerResources : testWorkerResources;

        if (!resolvedWorkerResources.isPresent()) {
            if (!getWorkerResolver().hasService(service)) {
                throw new JobManagerException(ComputeErrorCodes.INVALID_SERVICE_NAME,
                        String.format("Unknown service '%s' in query", service));
            } else if (!getWorkerResolver().hasService(makeKey(service, portfolioVersion))) {
                throw new JobManagerException(ComputeErrorCodes.INVALID_PORTFOLIO_VERSION,
                        String.format("Unknown portfolioVersion '%s' for service '%s' in query", portfolioVersion,
                                service));
            } else {
                throw new JobManagerException(ComputeErrorCodes.INVALID_WORKER_NAME,
                        String.format("Unknown worker '%s' for service '%s' and portfolioVersion '%s' in query",
                                worker, service, portfolioVersion));
            }
        }
        return resolvedWorkerResources.get();
    }

    @AllArgsConstructor
    public class SFNResponse {
        @JsonProperty("jobId")
        private final String jobId;
        @JsonProperty("worker")
        private final String worker;
        @JsonProperty("tags")
        private final List<String> tags;
        @JsonProperty("heartbeatTimeout")
        private final Integer heartbeatTimeout;
    }

    private String createSFNExecution(@NonNull final JobDBRecord jobRecord, final ServiceResources.WorkerResources workerResources) throws JobManagerException {

        log.info("createSFNExecution:workerResources: {}", workerResources);

        try {
            final SFNResponse response = new SFNResponse(jobRecord.getJobId(), workerResources.getWorkerIdentifier(), jobRecord.getTags(),
                    workerResources.getComputeSpecification().getHeartbeatTimeoutSeconds());

            final String json = Json.mapper.writeValueAsString(response);
            log.info("createSFNExecution response: {}", json);

            final StartExecutionRequest startExecutionRequest = new StartExecutionRequest()
                    .withStateMachineArn(workerResources.getStateMachineArn()).withInput(json);
            final StartExecutionResult startExecutionResult;
            if (workerResources.getDeploymentType() == ServiceResources.ServiceDeployment.TEST) {
                getMetrics().reportTestJobSubmitted(jobRecord.getService(), jobRecord.getWorker());
            }

            startExecutionResult = sfn.startExecution(startExecutionRequest);
            if (workerResources.getDeploymentType() == ServiceResources.ServiceDeployment.TEST) {
                getMetrics().reportTestJobSubmitted(jobRecord.getService(), jobRecord.getWorker());
            }
            log.info("StartExecutionResult: {}", startExecutionResult);
            return startExecutionResult.getExecutionArn();

        } catch (final JsonProcessingException e) {
            throw new JobManagerException(ComputeErrorCodes.SERVICE_DISCOVERY_EXCEPTION, "createSFNExecution", e);
        } catch (final ExecutionLimitExceededException | StateMachineDeletingException e) {
            log.error("Failed to start SFN execution", e);
            throw new JobManagerException(ComputeErrorCodes.RESOURCE_CREATION_ERROR, e);
        } catch (final AWSStepFunctionsException e) {
            log.error("Failed to start SFN execution", e);
            throw new JobManagerException(ComputeErrorCodes.AWS_BAD_REQUEST, e);
        }
    }

    private void createStepfunctionExecution(final MDCLoader loader, final JobDBRecord jobRecord,
                                             final ServiceResources.WorkerResources workerResources) throws JobManagerException {
        // The service integration step function gets started when the job transitions into RUNNING in AWS batch.
        // So nothing to do here, now.
        if (jobRecord.isBatch())
            return;
        Duration durationAddStepExecutionArn = Duration.ofSeconds(0);
        try {
            final Instant callStart = Instant.now();
            final String stepExecutionArn = createSFNExecution(jobRecord, workerResources);
            loader.withField(MDCFields.STEP_EXECUTION_ARN, stepExecutionArn);
            jobStore.addStepExecutionArn(jobRecord, stepExecutionArn);
            durationAddStepExecutionArn = Duration.between(callStart, Instant.now());
        } catch (final ComputeException e) {
            final String error = makeString("Failed to store step execution arn on DynamoDB for JobID: ", jobRecord.getJobId());
            log.error(error, e);
            safelyUpdateJobStatusToFailed(jobRecord, error);
            throw new JobManagerException(e);
        } catch (final JobManagerException e) {
            final String error = makeString("Failed to create step function execution for jobID: ", jobRecord.getJobId());
            log.error(error, e);
            safelyUpdateJobStatusToFailed(jobRecord, error);
            throw e;
        }
        getMetrics().reportCreateJobAddStepExecutionArnDuration(durationAddStepExecutionArn.toMillis() / 1_000.0);
    }

    public List<KeyValuePair> gatherServiceIntegrationEnvironment(final String jobId, final String service, final String worker,
                                                                  final String payload, final List<String> argTags,
                                                                  final ServiceResources.BatchWorkerResources workerResources) throws JobManagerException {
        String tags = "[]";
        if (argTags != null && !argTags.isEmpty()) {
            final ArrayNode array = Json.mapper.createArrayNode();
            argTags.stream().forEach(array::add);
            try {
                tags = Json.mapper.writeValueAsString(array);
            } catch (final JsonProcessingException e) {
                throw new JobManagerException(ComputeErrorCodes.BAD_REQUEST, makeString("Worker ", service, ":",
                        worker, " job tags could not be serialized to json"), e);
            }
        }

        final int heartbeatTimeout = firstNonNull(workerResources.getComputeSpecification().getHeartbeatTimeoutSeconds(), defaultComputeSpecification.getHeartbeatTimeoutSeconds());
        final ArrayList<KeyValuePair> environmentVariables = new ArrayList<>();
        addVariable(environmentVariables, "COS_HEARTBEAT_TIMEOUT", Integer.toString(heartbeatTimeout),
                MAX_ENVIRONMENT_VARIABLE_LENGTH);
        addVariable(environmentVariables, "COS_JOB_SECRET", jobId, MAX_ENVIRONMENT_VARIABLE_LENGTH);
        addVariable(environmentVariables, "COS_JOB_TAGS", tags, MAX_ENVIRONMENT_VARIABLE_LENGTH);
        addVariable(environmentVariables, "COS_JOB_ID", jobId, MAX_ENVIRONMENT_VARIABLE_LENGTH);
        boolean sendPayload = !defaultBatchJobOptions.getNeverSetJobPayload();
        if (workerResources.getJobDefinition().getBatchJobOptions() != null
                && workerResources.getJobDefinition().getBatchJobOptions().getNeverSetJobPayload() != null) {
            sendPayload = !workerResources.getJobDefinition().getBatchJobOptions().getNeverSetJobPayload();
        }
        if (sendPayload)
            addVariable(environmentVariables, "COS_JOB_PAYLOAD", payload, MAX_ENVIRONMENT_VARIABLE_LENGTH);
        return environmentVariables;
    }

    private ArrayList<KeyValuePair> addVariable(final ArrayList<KeyValuePair> variables, final String key, final String value,
                                                final int maxLength) {
        if (value == null) {
            log.info("Excluding variable {} because its value is null", key);
        } else if (value.length() < maxLength) {
            variables.add(new KeyValuePair().withName(key).withValue(value));
        } else {
            log.info("Excluding variable {} because its value is longer than {}", key, maxLength);
        }
        return variables;
    }

    public void safelyUpdateJobStatusToFailed(final JobDBRecord jobRecord, final String error) {
        safelyUpdateJobStatusToFailed(jobRecord.getJobId(), jobRecord.getIdempotencyId(), error);
    }

    public void safelyUpdateJobStatusToFailed(final String jobId, final String idempotencyId, final String error) {
        try {
            // The database object for each row must look like a Failure.
            final Failure failure = JsonHelper.parseFailure(error);
            jobStore.markJobFailed(jobId, idempotencyId, Lists.newArrayList(failure), null, null);
        } catch (final ComputeException e) {
            log.error("Failed to set status to failed", e);
        }
    }

    private String validateBatchJobCreation(final MDCLoader loader, final JobDBRecord jobRecord, final ServiceResources.WorkerResources workerResources)
            throws JobManagerException {
        String batchJobId = NONE_JOB_ID;
        if (jobRecord.isBatch()) {
            batchJobId = validateBatchJobCreation(loader,
                    new BatchJobCreationArgs(jobRecord.getJobId(), Arrays.asList(jobRecord), jobRecord.getService(),
                            jobRecord.getWorker(), jobRecord.getResolvedPortfolioVersion(), jobRecord.getIdempotencyId(),
                            jobRecord.findPayload(), jobRecord.getTags()), (ServiceResources.BatchWorkerResources) workerResources);
        }
        return batchJobId;
    }

    private String validateBatchJobCreation(final MDCLoader loader, final BatchJobCreationArgs args,
                                            final ServiceResources.BatchWorkerResources workerResources) throws JobManagerException {
        try {
            final String batchJobId = createBatchJobWithRetries(args.getJobId(), args.getJobDBRecords().size(),
                    args.getService(), args.getWorker(), args.getPortfolioVersion(), args.getPayload(),
                    args.getTags(), args.getJobDBRecords().get(0).getBatchQueueType(), workerResources);
            // This is not an array job
            if (args.getJobDBRecords().size() == 1) {
                jobStore.setBatchJobId(args.getJobDBRecords().get(0).getJobId(), batchJobId);
            } else {
                // For array job we map each batch job in array to compute job as
                // array-batch-job-id:{index} maps to compute-job-id:{index}
                for (final JobDBRecord record : args.getJobDBRecords()) {
                    final String batchId = record.getJobId().replace(args.getJobId(), batchJobId);
                    jobStore.setBatchJobId(record.getJobId(), batchId);
                }
            }
            loader.withField(MDCFields.BATCH_ID, batchJobId);
            return batchJobId;
        } catch (final ComputeException e) {
            final String error = makeString("Failed to store batch execution id on DynamoDB for jobID: ", args.getJobId());
            log.error(error, e);
            safelyUpdateJobStatusToFailed(args.getJobId(), args.getIdempotencyId(), error);
            throw new JobManagerException(e);
        } catch (final JobManagerException e) {
            final String error = makeString("Failed to create batch execution for jobID: ", args.getJobId());
            log.error(error, e);
            safelyUpdateJobStatusToFailed(args.getJobId(), args.getIdempotencyId(), error);
            throw e;
        }
    }
    
    // Since this method is called async from deleteArrayJob, making it public for testing.
    public void cancelJobs(final String arrayJobId, final String arrayBatchJobId) {
        //Shut down whole array job, that internally cancels all jobs in the array.
        if (!StringUtils.isNullOrEmpty(arrayBatchJobId)) {
            getComputeBatchDriver().verifyBatchJobTerminated(arrayBatchJobId,
                    makeString("Array batch job canceled due to user canceling compute job ", arrayJobId));
        }
        cancelJobs(arrayJobId);
    }

    // Since this method is called async from deleteArrayJob, making it public for testing.
    public void cancelJobs(final String arrayJobId) {
        SearchJobsResult searchJobsResult = null;
        String nextToken = null;
        do {
            try {
                searchJobsResult = jobStore.searchArrayJob(arrayJobId, nextToken);
                nextToken = searchJobsResult.getNextToken();
            } catch (final ComputeException e) {
                log.error(makeString("cancelJobs: Error during searchArrayJob the JobId: ", arrayJobId), e);
                nextToken = null;
            }
            for (final JobDBRecord job : searchJobsResult.getJobs()) {
                try {
                    cancelJob(job);
                } catch (final JobManagerException e) {
                    log.error(makeString("cancelJobs: Error during cancelJob the JobId: ", job.getJobId()), e);
                }
            }
        } while (!StringUtils.isNullOrEmpty(nextToken));
    }

    public void cancelJob(final JobDBRecord job) throws JobManagerException {
        try {
            final JobStatus jobStatus = JobStatus.valueOf(job.getStatus());
            if (jobStatus.isTerminalState()) {
                metrics.reportJobCancelFailed(job.getService(), job.getWorker());
                throw new JobManagerException(ComputeErrorCodes.BAD_REQUEST,
                        "Completed, failed, or already canceled jobs cannot be canceled",
                        jobStore.getErrorDetailsForJob(JsonHelper.convertToJob(job)));
            }

            jobStore.safelyRemoveIfQueued(job);

            // The step function execution is started by the STARTING -> RUNNING
            // trigger in service integration workflows.
            if (!job.isBatch()) {
                final List<String> stepExecutionArns = job.getStepExecutionArns();
                if (CollectionUtils.isNotEmpty(stepExecutionArns)) {
                    // stop the last step execution in the list.
                    polling.stopSFNExecutionAndIgnoreIfMissing(stepExecutionArns.get(stepExecutionArns.size() - 1));
                }
            } else {
                //Shut down the batch container
                if (!StringUtils.isNullOrEmpty(job.getSpawnedBatchJobId())) {
                    getComputeBatchDriver().verifyBatchJobTerminated(job.getSpawnedBatchJobId(),
                            makeString("Batch job canceled due to user canceling compute job ", job.getJobId()));
                }
            }

            // mark job as canceled in DynamoDB
            jobStore.markJobCanceled(job);
            metrics.reportJobCanceled(job.getService(), job.getWorker());
        } catch (final ComputeException | JobManagerException e) {
            metrics.reportJobCancelFailed(job.getService(), job.getWorker());
            e.setDetailsField(jobStore.getErrorDetailsForJob(JsonHelper.convertToJob(job), e.getDetails()), e.getClass());
            wrapAndRethrow(e);
        }
    }

    public void sendQueueIdToSQS(final String queueId) {
        final ImmutableMap.Builder<String, MessageAttributeValue> attributeMap = ImmutableMap.<String, MessageAttributeValue>builder()
                .put(ComputeConstants.DynamoFields.QUEUE_ID, new MessageAttributeValue().withDataType("String").withStringValue(queueId));

        final SendMessageRequest request = new SendMessageRequest()
                .withQueueUrl(computeConfig.getTerminalJobsSqsUrl())
                .withMessageBody(queueId)
                .withMessageGroupId(queueId)
                .withMessageDeduplicationId(java.util.UUID.randomUUID().toString())
                .withMessageAttributes(attributeMap.build());
        try {
            final SendMessageResult sendMessageResult = getAmazonSQS().sendMessage(request);
            log.info("SQS message sent, messageId: {}", sendMessageResult.getMessageId());
        } catch (final Exception e) {
            log.error("Failed to send SQS message", e);
        }
    }

}
