package com.autodesk.compute.jobmanager.api.impl;

import com.amazonaws.util.StringUtils;
import com.autodesk.compute.auth.oauth.OxygenSecurityContext;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.*;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.discovery.ServiceResources;
import com.autodesk.compute.discovery.WorkerResolver;
import com.autodesk.compute.jobmanager.api.JobsApiService;
import com.autodesk.compute.jobmanager.api.NotFoundException;
import com.autodesk.compute.jobmanager.util.JobHelper;
import com.autodesk.compute.jobmanager.util.JobManagerException;
import com.autodesk.compute.jobmanager.util.JobStoreHelper;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.cosv2.ComputeSpecification;
import com.autodesk.compute.model.cosv2.NameValueDefinition;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.util.JsonHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.autodesk.compute.common.ComputeStringOps.*;
import static com.autodesk.compute.common.ComputeUtils.firstSupplier;
import static com.autodesk.compute.common.ServiceWithVersion.isCurrentVersion;
import static com.autodesk.compute.common.ServiceWithVersion.makeKey;
import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.firstNonEmpty;

@Slf4j
@RequestScoped
@Default
public class JobsApiServiceImpl implements JobsApiService {

  private static final String DEFAULT = "DEFAULT";
  private static final String JOB_ID_MUST_BE_SPECIFIED = "Job Id must be specified";
  @Context
  private UriInfo uriInfo;  //NOPMD

  private final WorkerResolver workerResolver;
  private final Metrics metrics;

  private final JobHelper jobHelper;
  private final JobStoreHelper jobStore;

  @Inject
  public JobsApiServiceImpl() {
    this(new JobStoreHelper(), new JobHelper(), null);
  }

  public JobsApiServiceImpl(
          final JobStoreHelper jobStore,
          final JobHelper schedule,
          final WorkerResolver workerResolver) {
    this.workerResolver = firstSupplier(() -> workerResolver, () -> WorkerResolver.getInstance());
    this.metrics = Metrics.getInstance();
    this.jobStore = jobStore;
    this.jobHelper = schedule;
  }

  // public for mocking
  public ComputeConfig getComputeConfig() {
    return ComputeConfig.getInstance();
  }

  public ServiceResources.WorkerResources getWorkerResources(final String service, final String worker,
                                                             final String portfolioVersion, final boolean noBatch) throws JobManagerException {
    Optional<ServiceResources.WorkerResources> testWorkerResources = Optional.empty();
    Optional<ServiceResources.WorkerResources> deployedWorkerResources = Optional.empty();
    Optional<ServiceResources.WorkerResources> resolvedWorkerResources = Optional.empty();
    try {
      // Figure out if the worker making the API call is the currently deployed version.
      // If it is, then give it a current version job. Otherwise give it a test job.
      // An exception to that rule: if noBatch = true, then we attempt to give it a test worker
      // only, while validating that it must be a batch worker's configuration.
      boolean currentlyDeployedWorker = false;
      if (isCurrentVersion(portfolioVersion)) {
        deployedWorkerResources = workerResolver.getWorkerResources(service, worker,
                portfolioVersion);
        currentlyDeployedWorker = true;
      } else {
        testWorkerResources = workerResolver.getWorkerResources(service, worker, portfolioVersion);

      if (!ComputeConfig.isCosV3()) {
          deployedWorkerResources = workerResolver.getWorkerResources(service, worker, null);
          // NOTE: in v2, portfolioVersion is never null in workerResources, in v3, it might be null or maybe just during scaffolding/development
          currentlyDeployedWorker = deployedWorkerResources
                  .map(workerResources -> workerResources.getPortfolioVersion() != null && workerResources.getPortfolioVersion().equals(portfolioVersion))
                  .orElse(false);
        }
      }

      resolvedWorkerResources = currentlyDeployedWorker ? deployedWorkerResources : testWorkerResources;

      validateResolvedWorkerResources(service, worker, portfolioVersion, noBatch, resolvedWorkerResources);
    } catch (final ComputeException e) {
      throw new JobManagerException(e);
    }
    return resolvedWorkerResources.get();
  }

  private void validateResolvedWorkerResources(final String service, final String worker, final String portfolioVersion, final boolean noBatch, final Optional<ServiceResources.WorkerResources> resolvedWorkerResources) throws JobManagerException {
    if (!resolvedWorkerResources.isPresent()) {
      if (!workerResolver.hasService(service)) {
        throw new JobManagerException(ComputeErrorCodes.INVALID_JOB_PAYLOAD,
                String.format("Unknown service '%s' in query", service));
      } else if (!workerResolver.hasService(makeKey(service, portfolioVersion))) {
        throw new JobManagerException(ComputeErrorCodes.INVALID_JOB_PAYLOAD,
                String.format("Unknown portfolioVersion '%s' for service '%s' in query", portfolioVersion,
                        service));
      } else {
        throw new JobManagerException(ComputeErrorCodes.INVALID_JOB_PAYLOAD,
                String.format("Unknown worker '%s' for service '%s' and portfolioVersion '%s' in query",
                        worker, service, portfolioVersion));
      }
    } else {
      // One other case to verify: noBatch is *only* allowed for batch workers.
      final ServiceResources.WorkerResources resource = resolvedWorkerResources.get();
      final boolean isBatch = resource instanceof ServiceResources.BatchWorkerResources;
      if (noBatch && !isBatch) {
        log.error("NoBatch is only allowed for batch workers -- moniker {} with worker {} needs to fix how it submits jobs", service, worker);
      }
    }
  }

  private void reportJobSubmitMetric(final String moniker, final String worker, final boolean useServiceIntegration) {
    if (useServiceIntegration) {
      metrics.reportServiceIntegrationJobSubmitted(moniker, worker);
    } else {
      metrics.reportPollingJobSubmitted(moniker, worker);
    }
  }

  @Override
  @SneakyThrows(ComputeException.class)
  public Response createJob(@NonNull final JobArgs jobArgs, final Boolean argNoBatch, final SecurityContext securityContext)
          throws JobManagerException {
    final boolean noBatch = firstNonNull(argNoBatch, false);

    try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "createJob")
            .withField(MDCFields.WORKER, jobArgs.getWorker())
            .withField(MDCFields.SERVICE, firstNonNull(jobArgs.getService(), MDCFields.UNSPECIFIED))) {

      final OxygenSecurityContext oxygenContext = getOxygenSecurityContextAndValidate(securityContext, loader, jobArgs.getService(), jobArgs.getWorker());

      // Log noBatch metrics
      if (noBatch)
        metrics.reportNoBatchJobSubmitted(jobArgs.getService(), jobArgs.getWorker());

      // Check to see if this is a batch worker
      final ServiceResources.WorkerResources workerResources =
              getWorkerResources(jobArgs.getService(), jobArgs.getWorker(), jobArgs.getPortfolioVersion(), noBatch);
      final ComputeSpecification computeSpecification = workerResources.getComputeSpecification();

      // We do not enforce jobConcurrencyLimit for TEST workers
      UserTypeConcurrencyLimit userTypeConcurrencyLimit = null;
      if (workerResources.getDeploymentType() == ServiceResources.ServiceDeployment.DEPLOYED) {
        userTypeConcurrencyLimit = getJobConcurrencyLimit(jobArgs.getUserType(), computeSpecification);
      }

      final boolean isBatchWorker = workerResources instanceof ServiceResources.BatchWorkerResources;
      // We allow nobatch for both test and deployed workers... scary monsters
      if (noBatch && isBatchWorker &&
              workerResources.getDeploymentType() != ServiceResources.ServiceDeployment.TEST) {
        log.warn("Submitting a nobatch job for a deployed batch worker. Make sure you know what you're doing!");
      }

      final String batchQueueType = isBatchWorker ? (StringUtils.isNullOrEmpty(jobArgs.getUserType()) ? DEFAULT : jobArgs.getUserType()) : null;

      // Check if the job with idempotencyId already exists
      jobStore.validateIdempotency(jobArgs);

      // Create new Job ID
      final String jobID = JobDBRecord.newJobId();
      loader.withField(MDCFields.JOB_ID, jobID);
      metrics.reportJobSubmitted(jobArgs.getService(), jobArgs.getWorker());

      // default: component and lambda workers
      // have no batch job ID
      final boolean isBatchJob = workerResources instanceof ServiceResources.BatchWorkerResources && !noBatch;

      reportJobSubmitMetric(jobArgs.getService(), jobArgs.getWorker(), isBatchJob);

      loader.withField("requestedPortfolioVersion", firstNonNull(jobArgs.getPortfolioVersion(), "undefined"));
      loader.withField("resolvedPortfolioVersion", firstNonNull(workerResources.getPortfolioVersion(), "undefined"));

      final String serviceClientId = makeServiceClientId(oxygenContext.getUserId(), oxygenContext.getClientId(),
              jobArgs.getService());

      // Insert the progress table record before submitting the step function execution,
      // to make certain that the record will be found when needed.
      final String userType = userTypeConcurrencyLimit == null ? null : userTypeConcurrencyLimit.userType;
      final String userId = firstNonEmpty(oxygenContext.getUserId(), oxygenContext.getClientId());
      final JobDBRecord jobRecord = jobStore.insertDBRecord(jobArgs, jobID, oxygenContext.getBearerToken(), serviceClientId,
              workerResources, userId, userType, isBatchJob, batchQueueType);

      log.info("createJob.insertDBRecord - Service:{}, Worker:{}, JobID:{}, Status:{}",
              jobRecord.getService(), jobRecord.getWorker(), jobRecord.getJobId(), jobRecord.getStatus());
      return scheduleJob(jobArgs, loader, workerResources, userTypeConcurrencyLimit, jobRecord);

    }
  }


  @Override
  @SneakyThrows(ComputeException.class)
  public Response createJobs(final ArrayJobArgs jobArgs, final SecurityContext securityContext)
          throws JobManagerException {

    try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "createJobs")
            .withField(MDCFields.WORKER, jobArgs.getWorker())
            .withField(MDCFields.SERVICE, firstNonNull(jobArgs.getService(), MDCFields.UNSPECIFIED))) {

      final OxygenSecurityContext oxygenContext = getOxygenSecurityContextAndValidate(securityContext, loader, jobArgs.getService(), jobArgs.getWorker());


      // Check to see if this is a batch worker
      final ServiceResources.WorkerResources workerResources =
              getWorkerResources(jobArgs.getService(), jobArgs.getWorker(), jobArgs.getPortfolioVersion(), false);

      // Create a new Job ID for the whole array
      final String arrayJobID = JobDBRecord.newArrayJobId();
      loader.withField(MDCFields.JOB_ID, arrayJobID);
      // We need to track both overall number of job and array job metrics
      for (int i = 0; i < jobArgs.getJobs().size(); i++) {
        metrics.reportJobSubmitted(jobArgs.getService(), jobArgs.getWorker());
      }
      metrics.reportArrayJobSubmitted(jobArgs.getService(), jobArgs.getWorker());

      // default: component and lambda workers
      // have no batch job ID
      final boolean isBatchJob = workerResources instanceof ServiceResources.BatchWorkerResources;


      reportJobSubmitMetric(jobArgs.getService(), jobArgs.getWorker(), isBatchJob);

      loader.withField("requestedPortfolioVersion", firstNonNull(jobArgs.getPortfolioVersion(), "undefined"));
      loader.withField("resolvedPortfolioVersion", firstNonNull(workerResources.getPortfolioVersion(), "undefined"));

      final String serviceClientId = makeServiceClientId(oxygenContext.getUserId(), oxygenContext.getClientId(),
              jobArgs.getService());

      final String userId = firstNonEmpty(oxygenContext.getUserId(), oxygenContext.getClientId());
      final List<JobDBRecord> jobRecords = jobStore.insertDBRecords(jobArgs, arrayJobID, oxygenContext.getBearerToken(), serviceClientId,
              workerResources, userId, null, isBatchJob, isBatchJob ? DEFAULT : null);

      log.info("createJobs.insertDBRecords - Service:{}, Worker:{}, Num Jobs:{}",
              jobArgs.getService(), jobArgs.getWorker(), jobRecords.size());
      return scheduleArrayJob(jobArgs, loader, workerResources, arrayJobID, jobRecords);

    }
  }

  private OxygenSecurityContext getOxygenSecurityContextAndValidate(final SecurityContext securityContext, final MDCLoader loader,
                                                                    final String service, final String worker) throws JobManagerException, ComputeException {
    final OxygenSecurityContext oxygenContext = OxygenSecurityContext.get(securityContext);

    // TODO: Analyze if this is ever called
    if (!oxygenContext.isSecure()) {
      throw new JobManagerException(ComputeErrorCodes.OXYGEN_MISSING_TOKEN_ERROR, "Insecure call made");
    }

    validateNonEmptyOrThrow(service, "Service must not be null or empty");
    validateNonEmptyOrThrow(worker, "Worker must not be null or empty");
    return oxygenContext;
  }

  private Response scheduleJob(@NonNull final JobArgs jobArgs, final MDCLoader loader, final ServiceResources.WorkerResources workerResources,
                               final UserTypeConcurrencyLimit userTypeConcurrencyLimit, final JobDBRecord jobRecord) throws JobManagerException {
    // Fill in as much info from JobRecord (same method used by returning Job from GET Job and Search API)
    final Job job = JsonHelper.convertToJob(jobRecord);
    if (getComputeConfig().createJobsAsynchronously(jobArgs.getService())) {
      // Get the map for current context so we can set the same for Async thread.
      final Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();
      CompletableFuture.runAsync(() -> {
        try (final MDCLoader mcdLoader = MDCLoader.forFieldMap(mdcContextMap)) {
          scheduleOrQueueJob(jobArgs, mcdLoader, workerResources, userTypeConcurrencyLimit, jobRecord, job);
        } catch (final JobManagerException e) {
          // We already have retry logic on apis for DynamoDB, Batch and Step,
          // nothing more can be done here, log and post metric
          log.error("createJob: asynchronous job schedule failed", e);
          metrics.reportAsynchronousJobCreationFailed(jobArgs.getService(), jobArgs.getWorker());
        }
      });
      return Response.accepted().entity(job).build();
    } else {
      scheduleOrQueueJob(jobArgs, loader, workerResources, userTypeConcurrencyLimit, jobRecord, job);
      return Response.ok().entity(job).build();
    }
  }

  private Response scheduleArrayJob(final ArrayJobArgs jobArgs, final MDCLoader loader, final ServiceResources.WorkerResources workerResources,
                                    final String arrayJobId, final List<JobDBRecord> jobRecords) throws JobManagerException {
    // Fill in as much info from JobRecord (same method used by returning Job from GET Job and Search API)
    final ArrayJob arrayJobResponse = new ArrayJob();
    arrayJobResponse.setJobID(arrayJobId);
    arrayJobResponse.setService(jobArgs.getService());
    arrayJobResponse.setWorker(jobArgs.getWorker());
    arrayJobResponse.setPortfolioVersion(workerResources.getPortfolioVersion());

    // Get the map for current context so we can set the same for Async thread.
    final Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();
    // Always create it asynchronously
    CompletableFuture.runAsync(() -> {
      try (final MDCLoader mcdLoader = MDCLoader.forFieldMap(mdcContextMap)) {
        scheduleArrayJob(mcdLoader, jobArgs.getService(), jobArgs.getWorker(), workerResources, arrayJobId, jobRecords);
      } catch (final JobManagerException e) {
        // We already have retry logic on apis for DynamoDB, Batch and Step,
        // nothing more can be done here, log and post metric
        log.error("createJob: asynchronous job schedule failed", e);
        metrics.reportAsynchronousJobCreationFailed(jobArgs.getService(), jobArgs.getWorker());
      }
    });
    arrayJobResponse.setJobs(jobRecords.stream().map(JsonHelper::convertToJobInfo).collect(Collectors.toList()));

    return Response.accepted().entity(arrayJobResponse).build();
  }

  private void scheduleOrQueueJob(@NonNull final JobArgs jobArgs,
                                  final MDCLoader loader,
                                  final ServiceResources.WorkerResources workerResources,
                                  final UserTypeConcurrencyLimit userTypeConcurrencyLimit,
                                  final JobDBRecord jobRecord,
                                  final Job job) throws JobManagerException {
    try {
      // We were able to determine jobConcurrencyLimit for this request so enqueue the job
      if (userTypeConcurrencyLimit != null) {
        tryScheduleQueuedJob(loader, workerResources, userTypeConcurrencyLimit, jobRecord, job);
      } else {
        jobHelper.scheduleJob(loader, jobRecord, workerResources);
      }
    } catch (final JobManagerException xe) {
      metrics.reportJobSubmitFailed(jobArgs.getService(), jobArgs.getWorker());
      throw xe;
    }
  }

  private void scheduleArrayJob(final MDCLoader loader, final String service, final String worker,
                                final ServiceResources.WorkerResources workerResources,
                                final String arrayJobId, final List<JobDBRecord> jobRecords) throws JobManagerException {
    try {
      jobHelper.scheduleArrayJob(loader, arrayJobId, jobRecords, service, worker, workerResources);
    } catch (final JobManagerException xe) {
      metrics.reportJobSubmitFailed(service, worker);
      throw xe;
    }
  }

  private void tryScheduleQueuedJob(final MDCLoader loader,
                                    final ServiceResources.WorkerResources workerResources,
                                    final UserTypeConcurrencyLimit userTypeConcurrencyLimit,
                                    final JobDBRecord jobRecord,
                                    final Job job) throws JobManagerException {
    final String queueId = jobRecord.getUserServiceWorkerId();
    int activeCount = 0;
    try {
      activeCount = jobStore.getActiveJobsCount(queueId);
    } catch (final ComputeException e) {
      log.error("Failed to get active jobs count", e);
      if (ThrottleCheck.isThrottlingException(e)) {
        throw new JobManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, e);
      }
      throw new JobManagerException(ComputeErrorCodes.AWS_CONFLICT, "Failed to schedule the job, please try submitting the job again.", e);
    }
    if (activeCount >= userTypeConcurrencyLimit.jobConcurrencyLimit)
      return;

    final JobDBRecord aJobFromQueue = jobStore.peekJob(queueId);
    if (aJobFromQueue != null) {
      loader.withField(MDCFields.JOB_ID, aJobFromQueue.getJobId());
      log.info("tryScheduleQueuedJob: Scheduling job from queue, JobId: {}", aJobFromQueue.getJobId());
      try {
        jobHelper.scheduleJob(loader, aJobFromQueue, workerResources);
      } catch (final JobManagerException e) {
        // We fail to schedule a job:
        // Check if it was due to DynamoDB transaction conflict, if so let SQS path handle the scheduling by
        // simply sending a message to SQS
        if (e.getCode() == ComputeErrorCodes.DYNAMODB_TRANSACTION_CONFLICT) {
          jobHelper.sendQueueIdToSQS(queueId);
          job.setStatus(Status.QUEUED);
          metrics.reportJobScheduleDeferredOnConflict(job.getService(), job.getWorker());
          return;
        }

        // Else we tried scheduling the same job but we were not able to handle the exception, return it to user
        if (job.getJobID().equals(aJobFromQueue.getJobId())) {
          throw e;
        }
        log.error("tryScheduleQueuedJob: Failed to schedule a queued job, submitted JobId: {}, queued JobId: {}",
                job.getJobID(), jobRecord.getJobId());
      }
      try {
        jobStore.removeJobFromQueue(aJobFromQueue);
      } catch (final ComputeException e) {
        // We don't want the full exception / stack trace due to log bloat.
        // Failing this call is a common enough exception that we swallow it as we believe it's harmless.
        log.error("tryScheduleQueuedJob: Failed to dequeue the JobId: {}", aJobFromQueue.getJobId(), e);
      }
      if (jobRecord.getJobId().equals(aJobFromQueue.getJobId())) {
        job.setStatus(Status.SCHEDULED);
      }
    }
  }

  @AllArgsConstructor
  private static class UserTypeConcurrencyLimit {
    private final String userType;
    private final int jobConcurrencyLimit;
  }

  public UserTypeConcurrencyLimit getJobConcurrencyLimit(final String userType, final ComputeSpecification computeSpecification) throws JobManagerException {
    if (computeSpecification.getJobConcurrencyLimits() != null
            && !computeSpecification.getJobConcurrencyLimits().isEmpty()) {
      final Map<String, Integer> limits = computeSpecification.getJobConcurrencyLimits().stream()
              .collect(Collectors.toMap(NameValueDefinition::getName, NameValueDefinition::getValue));

      if (!isUserTypeValid(userType, limits)) { //NOPMD - Readability
        throw new JobManagerException(ComputeErrorCodes.BAD_REQUEST, "The worker has enforced jobConcurrencyLimits which requires " +
                "correct service defined value to be set for userType parameter");
      }
      if (userType != null && limits.containsKey(userType)) {
        return new UserTypeConcurrencyLimit(userType, limits.get(userType));
      } else {
        return new UserTypeConcurrencyLimit(DEFAULT, limits.get(DEFAULT));
      }
    }
    return null;
  }

  private boolean isUserTypeValid(final String userType, final Map<String, Integer> limits) {
    return StringUtils.isNullOrEmpty(userType) ? limits.containsKey(DEFAULT) : limits.containsKey(userType);
  }

  @Override
  public Response deleteJob(final String jobId, final SecurityContext securityContext) throws JobManagerException {
    try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "deleteJob")) {
      loader.withField(MDCFields.JOB_ID, jobId);
      if (ComputeStringOps.isNullOrEmpty(jobId)) {
        throw new JobManagerException(ComputeErrorCodes.BAD_REQUEST, JOB_ID_MUST_BE_SPECIFIED);
      }

      if (JobDBRecord.isValidArrayJobId(jobId)) {
        return deleteArrayJob(jobId, securityContext, loader);
      } else {
        return deleteJob(jobId, securityContext, loader);
      }
    }
  }

  public Response deleteJob(final String jobId, final SecurityContext securityContext, final MDCLoader loader) throws JobManagerException {
    // Get job from cache, asking it to get latest status from database.
    // Since we have multiple instances of Job Manager are running one may
    // have deleted the job and it may not be visible to others.
    final JobDBRecord job = getJobDBRecord(jobId, loader);

    checkUserAuth(securityContext, job.getUserId());

    this.jobHelper.cancelJob(job);

    // mark this job in local cache as canceled.
    job.setStatus(Status.CANCELED.toString());

    // todo: clean up ACM resources
    return Response.ok().entity(job).build();
  }

  public Response deleteArrayJob(final String arrayJobId, final SecurityContext securityContext, final MDCLoader loader) throws JobManagerException {
    final String nextToken = null;

    // Get the first job from the array to check auth.
    final String jobId0 = makeString(arrayJobId, ":0");
    final JobDBRecord job0 = jobStore.getJobFromCache(jobId0, false); // does not require current record

    // no job found
    if (job0 == null) {
      throw new JobManagerException(ComputeErrorCodes.NOT_FOUND, makeString("Job ", arrayJobId, " was not found"));
    }

    loader.withField(MDCFields.SERVICE, job0.getService());
    loader.withField(MDCFields.WORKER, job0.getWorker());

    checkUserAuth(securityContext, job0.getUserId());

    String batchJobId = null;
    if (job0.isBatch()) {
      if (!StringUtils.isNullOrEmpty(job0.getSpawnedBatchJobId())) {
        // child in array jobs has batch job ids as <array_batch_id>:<index>
        batchJobId = job0.getSpawnedBatchJobId().substring(0, job0.getSpawnedBatchJobId().indexOf(':'));
      }
    }
    final String arrayBatchJobId = batchJobId;

    CompletableFuture.runAsync(() -> {
      jobHelper.cancelJobs(arrayJobId, arrayBatchJobId);
    });

    return Response.accepted().build();
  }

  @Override
  public Response getJob(final String id, final String nextToken, final SecurityContext securityContext) throws JobManagerException {
    try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "getJob")) {
      loader.withField(MDCFields.JOB_ID, id);
      if (ComputeStringOps.isNullOrEmpty(id))
        throw new JobManagerException(ComputeErrorCodes.BAD_REQUEST, JOB_ID_MUST_BE_SPECIFIED);

      log.info("getJob called with Id: {}", id);
      /* todo: check ACM resource access */

      if (JobDBRecord.isValidArrayJobId(id)) {
        final ArrayJobResult arrayJob = ofNullable(jobStore.getArrayJob(id, nextToken))
                .orElseThrow(() -> new JobManagerException(ComputeErrorCodes.NOT_FOUND, makeString("Job ", id, " was not found")));
        return Response.ok().entity(arrayJob).build();

      } else {
        final Job job = ofNullable(jobStore.getJobFromCache(id, false))
                .map(JsonHelper::convertToJob)
                .orElseThrow(() -> new JobManagerException(ComputeErrorCodes.NOT_FOUND, makeString("Job ", id, " was not found")));
        return Response.ok().entity(job).build();
      }
    }
  }

  private JobDBRecord getJobDBRecord(final String jobId, final MDCLoader loader) throws JobManagerException {
    final JobDBRecord job = jobStore.getJobFromCache(jobId, true);
    if (job == null) {
      throw new JobManagerException(ComputeErrorCodes.NOT_FOUND, "Could not find job with id: " + jobId);
    }
    loader.withField(MDCFields.SERVICE, job.getService());
    loader.withField(MDCFields.WORKER, job.getWorker());
    return job;
  }

  private void checkUserAuth(final SecurityContext securityContext, final String userIDForCreation) throws JobManagerException {
    final OxygenSecurityContext oxygenContext = OxygenSecurityContext.get(securityContext);
    final String currUserId = firstNonEmpty(oxygenContext.getUserId(), oxygenContext.getClientId());
    // Check if the user is the job creator
    if (Objects.equals(currUserId, userIDForCreation)) {
      return;
    }
    throw new JobManagerException(ComputeErrorCodes.NOT_AUTHORIZED, makeString("The job operation is not authorized for this user "));
  }

  @Override
  public Response addTag(final String jobId, final String tag, final SecurityContext securityContext) throws
          NotFoundException {
    try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "addTag")) {
      final List<String> tags = getJobTags(jobId, securityContext, loader);
      if (!tags.contains(tag)) {
        tags.add(tag);
        jobStore.updateJobTags(jobId, tags);
        return Response.ok().build();
      } else {
        return Response.notModified().header("message", "The tag does exist already").build();
      }
    }
  }

  @Override
  public Response deleteTag(final String jobId, final String tag, final SecurityContext securityContext) throws
          NotFoundException {
    try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "deleteTag")) {
      final List<String> tags = getJobTags(jobId, securityContext, loader);
      if (tags.contains(tag)) {
        tags.remove(tag);
        jobStore.updateJobTags(jobId, tags);
        return Response.ok().build();
      } else {
        return Response.notModified().header("message", "The tag does not exist").build();
      }
    }
  }

  // check update auth before return job tags
  private List<String> getJobTags(final String jobId, final SecurityContext securityContext, final MDCLoader loader) throws JobManagerException {
    loader.withField(MDCFields.JOB_ID, jobId);
    if (ComputeStringOps.isNullOrEmpty(jobId)) {
      throw new JobManagerException(ComputeErrorCodes.BAD_REQUEST, JOB_ID_MUST_BE_SPECIFIED);
    }
    final JobDBRecord job = getJobDBRecord(jobId, loader);

    checkUserAuth(securityContext, job.getUserId());

    return job.getTags();
  }

}
