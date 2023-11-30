package com.autodesk.compute.testlambda;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.RetryPolicy;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.util.StringUtils;
import com.autodesk.compute.common.*;
import com.autodesk.compute.common.model.*;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.HealthReport;
import com.autodesk.compute.model.JobType;
import com.autodesk.compute.model.RunJobResult;
import com.autodesk.compute.model.SlackMessage;
import com.autodesk.compute.testlambda.gen.ApiClient;
import com.autodesk.compute.testlambda.gen.ApiException;
import com.autodesk.compute.testlambda.gen.DevelopersApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;

import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static java.util.stream.Collectors.*;

@Slf4j
public class TestLambdaProcessor {
    public static final String TEST_LAMBDA_JOB = "testLambdaJob";
    public static final String PAYLOAD = "{\"testDefinition\":{\"duration\":20,\"progressInterval\":2,\"progressUntil\":100,\"heartbeatInterval\":10,\"heartbeatUntil\":100,\"output\":{\"status\":\"COMPLETE\"}}}";
    private static final Config conf = Config.getInstance();

    private final String portfolioVersion = Optional.ofNullable(conf.getCloudosPortfolioVersion())
            .filter(version -> !StringUtils.isNullOrEmpty(version))
            .filter(version -> version.contentEquals("null"))
            .orElse("Test");
    private final List<String> completedJobs = Collections.synchronizedList(new ArrayList<>());
    private final List<String> submittedJobs = Collections.synchronizedList(new ArrayList<>());
    @Getter
    private final List<SlackMessage.Attachment> slackAttachments = Collections.synchronizedList(new ArrayList<>());
    private List<String> foundJobs;
    private final AtomicInteger numFailedJobs = new AtomicInteger();
    private final AtomicInteger numInprogressJobs = new AtomicInteger();
    private final SlackClient slack;
    private final Optional<SNSClient> sns;

    private final OxygenClient oxygen;
    private final String oxygenToken;

    public TestLambdaProcessor() {
        // We do not have job notification configured for cosv3 worker schema, hence no need to initialize SNSClient,
        // as it also depends on test SQS queue which we do not have and need for cosv3 ATM.
        this(new SlackClient(), Optional.ofNullable(ComputeConfig.isCosV3() ? null : new SNSClient()), makeOxygenClient());
    }

    @Inject
    public TestLambdaProcessor(final SlackClient slackClient, final Optional<SNSClient> snsClient, final OxygenClient oxygenClient) {
        slack = slackClient;
        sns = snsClient;
        oxygen = oxygenClient;
        try {
            oxygenToken = oxygen.getNewCloudOSComputeToken();
        } catch (final ComputeException e) {
            log.error("Unable to get oxygen token", e);
            throw new RuntimeException(e);
        }
    }

    public static OxygenClient makeOxygenClient() {
        return new OxygenClient(conf.getOxygenUrl(), conf.getOxygenClientId(), conf.getOxygenClientSecret(), ComputeUtils.TimeoutSettings.FAST);
    }

    public void process() {  //NOPMD
        log.info(makeString("Starting test-lambda job handler v", portfolioVersion));

        final List<CompletableFuture<RunJobResult>> submitJobFutures = Collections.synchronizedList(new ArrayList<>());
        final Map<JobType, List<RunJobResult>> jobResultsPerJobType = Arrays.stream(JobType.values()).collect(  //NOPMD
                toMap(item -> item, item -> Collections.synchronizedList(new ArrayList<>()))
        ); // Populated by validateJobs

        slackAttachments.add(
                SlackClient.toAttachment(
                        makeString("Testing ", conf.getBatchJobsCount(), " Batch, ", conf.getEcsJobsCount(), " ECS, and ", conf.getGpuJobsCount(), " GPU jobs"),
                        SlackMessage.MessageSeverity.GOOD));

        final Map<String, List<Job>> recentJobs = searchRecentJobsGroupingByWorker();
        for (final JobType jobType : JobType.values()) {
            final List<Job> existingJobsForWorker = recentJobs.getOrDefault(jobType.getWorker(), Collections.emptyList());
            // TODO: this function has a bunch of side-effects for a verification function; split it in two, or three
            verifyJobs(existingJobsForWorker, jobType, jobResultsPerJobType.get(jobType), submitJobFutures);
        }

        // wait for all tasks to complete before continuing
        CompletableFuture.allOf(submitJobFutures.toArray(new CompletableFuture<?>[0])).join();

        if (sns.isPresent()) {
            sns.get().processSnsMessages(slackAttachments, completedJobs);
        }

        try {
            final HealthReport.HealthReportBuilder healthReportBuilder = HealthReport.builder()
                    .batchJobStatus(reduceJobResults(jobResultsPerJobType.get(JobType.BATCH)))
                    .queuedBatchJobsStatus(findQueuedJobStatus(jobResultsPerJobType.get(JobType.BATCH), JobType.BATCH))
                    .ecsJobStatus(reduceJobResults(jobResultsPerJobType.get(JobType.ECS)))
                    .queuedEcsJobsStatus(findQueuedJobStatus(jobResultsPerJobType.get(JobType.ECS), JobType.ECS))
                    .gpuJobStatus(reduceJobResults(jobResultsPerJobType.get(JobType.GPU)))
                    .jobSearchStatus(testSubmittedJobsAppearInSearch());
            sendHealthReportToS3(healthReportBuilder.build());
        } catch (final Exception e) {
            slackAttachments.add(SlackClient.toAttachment(" failed to write health report to S3", e));
        }

        final int attemptedJobs = Arrays.stream(JobType.values()).map(JobType::getJobCount).mapToInt(i -> i).sum();

        final SlackMessage.MessageSeverity attemptsSeverity = numInprogressJobs.get() == 0 ?
                SlackMessage.MessageSeverity.GOOD : SlackMessage.MessageSeverity.WARNING;
        slackAttachments.add(
                SlackClient.toAttachment(
                        makeString("Found ", getCompletedJobs().size(), " completed, ", numFailedJobs.get(),
                                " failed, ", numInprogressJobs.get(), " inprogress jobs from ", attemptedJobs,
                                " previously attempted jobs"),
                        attemptsSeverity));

        slack.postToSlack(slackAttachments);
    }


    /**
     * Checks to see if there are any non terminal jobs found in first numJobs from recentJobs,
     * and if they are within timout period monitor progress on them instead of starting new ones.
     */
    public void verifyJobs(final List<Job> recentJobsForWorker, final JobType jobType,
                           final List<RunJobResult> jobResults, final List<CompletableFuture<RunJobResult>> futures) {
        // Check to see how many jobs out of first numJobs are in COMPLETED, FAILED or in non terminal states)
        final List<Job> nonTerminalJobs = new ArrayList<>();
        int numNewJobsToStart = jobType.getJobCount();

        final Map<Status, List<Job>> workerJobsByStatus = recentJobsForWorker.stream().limit(jobType.getJobCount()).collect(
                groupingBy(Job::getStatus)
        );

        // Walk through the jobs by status. Some are logged, some are canceled...
        for (final Map.Entry<Status, List<Job>> currentEntry : workerJobsByStatus.entrySet()) {
            switch (currentEntry.getKey()) {
                // Log the successful jobs...
                case COMPLETED:
                    currentEntry.getValue().stream()
                            .map(job -> new RunJobResult(HealthReport.TestResult.SUCCESS, job))
                            .forEach(result -> {
                                jobResults.add(result);
                                completedJobs.add(result.getJob().getJobID());
                            });
                    break;
                // And log the failed ones. These should wake up our alarms.
                case FAILED:
                    currentEntry.getValue().stream()
                            .map(job -> new RunJobResult(HealthReport.TestResult.FAILED, job))
                            .forEach(result -> {
                                numFailedJobs.incrementAndGet();
                                jobResults.add(result);
                                slackAttachments.add(SlackClient.toAttachment(
                                        jobType.getName() + " [ " + result.getJob().getJobID() + " ] has " + Status.FAILED,
                                        SlackMessage.MessageSeverity.DANGER));
                            });
                    break;
                case CANCELED:  // As you can see, previously canceled jobs are ignored
                    break;
                default:    // The jobs are still running. Are they okay?
                    // Split these up into timed out or not. Cancel the timed out jobs.
                    final long timeoutSeconds = jobType.getTimeoutSeconds();
                    final Map<Boolean, List<Job>> timedOutOrNot = currentEntry.getValue().stream()
                            .collect(partitioningBy(job -> hasTimedOut(job, timeoutSeconds)));
                    // Write errors and cancel the timed out jobs.
                    // We might be canceling jobs if the cluster is saturated, or if
                    // jobs aren't starting. If we are repeatedly doing this, then we should
                    // alert ourselves.
                    timedOutOrNot.getOrDefault(Boolean.TRUE, Collections.emptyList()).stream().forEach(
                            timedOutJob -> {
                                final String message = makeString(jobType.getName(), "[", timedOutJob.getJobID(), "] failed to finish in ",
                                        secondsToString(timeoutSeconds));
                                slackAttachments.add(SlackClient.toAttachment(message, SlackMessage.MessageSeverity.DANGER));
                                numFailedJobs.incrementAndGet();
                                cancelJob(timedOutJob.getJobID(), slackAttachments, SlackMessage.MessageSeverity.WARNING);
                                log.error(message);
                            }
                    );
                    // For jobs that have not yet timed out, let them run.
                    // We'll submit fewer jobs in the next round so we can watch their progress.
                    final List<Job> runningJobs = timedOutOrNot.getOrDefault(Boolean.FALSE, Collections.emptyList());
                    runningJobs.stream()
                            .map(job -> new RunJobResult(HealthReport.TestResult.SUCCESS, job))
                            .forEach(
                                result -> {
                                    // Inprogress jobs not timed out are still GOOD
                                    jobResults.add(result);
                                    numInprogressJobs.incrementAndGet();
                                    nonTerminalJobs.add(result.getJob());
                                }
                    );
                    numNewJobsToStart -= runningJobs.size();
                    break;
            }
        }

        // Cancel all the remaining jobs that are in progress, if any.
        recentJobsForWorker.stream().skip(jobType.getJobCount())
                .filter(job -> !JobStatus.isTerminalState(job.getStatus()))
                .forEach(job -> cancelJob(job.getJobID(), slackAttachments, SlackMessage.MessageSeverity.WARNING));

        // Start enough jobs to reach numJobs running at once.
        startNewJobs(jobType, jobResults, slackAttachments, futures, nonTerminalJobs, numNewJobsToStart);
    }

    private String secondsToString(final long seconds) {
        if (seconds < 60)
            return makeString(seconds, " seconds(s)");
        return makeString(seconds / 60, " minute(s)");
    }

    public Supplier<RunJobResult> submitJobRunnable(final JobType jobType, final List<RunJobResult> jobResults, final List<SlackMessage.Attachment> attachments) {
        return () -> {
            final RunJobResult result = submitNewJob(jobType);
            jobResults.add(result);
            return result;
        };
    }

    public <T> CompletableFuture<T> runAsync(final Supplier<T> runnable) {
        // The gross null check here is because the mocks attempt to execute
        // this method with null as an argument before allowing it to be overridden.
        if (runnable != null)
            return CompletableFuture.supplyAsync(runnable);
        return null;
    }

    private void startNewJobs(final JobType jobType, final List<RunJobResult> jobResults,
                              final List<SlackMessage.Attachment> attachments, final List<CompletableFuture<RunJobResult>> futures,
                              final List<Job> nonTerminalJobs, final int numNewJobsToStart) {
        // Start new jobs to replace failed or completed jobs
        final Supplier<RunJobResult> newJob = submitJobRunnable(jobType, jobResults, attachments);

        IntStream.range(0, numNewJobsToStart).mapToObj(n -> newJob).map(this::runAsync).forEach(futures::add);

        final SlackMessage.MessageSeverity attemptsSeverity = nonTerminalJobs.isEmpty() ?
                SlackMessage.MessageSeverity.GOOD : SlackMessage.MessageSeverity.WARNING;
        attachments.add(
                SlackClient.toAttachment(
                        makeString(jobType.getName(), " - submitted new: ", numNewJobsToStart, ", found existing: ",
                                nonTerminalJobs.size()), attemptsSeverity));
    }

    private HealthReport.TestResult reduceJobResults(final List<RunJobResult> jobResults) {
        return jobResults.stream().filter(
                result -> result.getTestResult() != HealthReport.TestResult.SUCCESS || result.getJob() == null)
                .findFirst().map(RunJobResult::getTestResult).orElse(HealthReport.TestResult.SUCCESS);
    }

    public HealthReport.TestResult findQueuedJobStatus(final List<RunJobResult> jobResults, final JobType jobType) {
        if (jobType.getJobCount() < 2 || jobResults.isEmpty()) {
            log.info("Too few {} jobs to check for queued status", jobType);
            return HealthReport.TestResult.NOT_CONFIGURED_HERE;
        }
        final List<String> jobsWhichHaveNeverBeenQueued = jobResults.stream()
                .map(RunJobResult::getJob)
                .filter(Objects::nonNull)
                .filter(job -> job.getStatus() != Status.QUEUED)
                .filter(job -> job.getStatusUpdates().stream().map(StatusUpdate::getStatus)
                        .noneMatch(status -> status == Status.QUEUED))
                .map(Job::getJobID).collect(toList());
        if (!jobsWhichHaveNeverBeenQueued.isEmpty()) {
            slackAttachments.add(SlackClient.toAttachment(
                    makeString("Could not find QUEUED status among the ", jobType, " jobs: ",
                            Arrays.toString(jobsWhichHaveNeverBeenQueued.toArray())), SlackMessage.MessageSeverity.DANGER));
            return HealthReport.TestResult.FAILED;
        } else {
            log.info("Every job was queued at some point among the {} jobs", jobType.getName());
            return HealthReport.TestResult.SUCCESS;
        }
    }

    public void cancelJob(final String jobID, final List<SlackMessage.Attachment> attachments, final SlackMessage.MessageSeverity warningLevel) {
        final DevelopersApi jobManagerClient = makeJobManagerClient(oxygenToken);
        try {
            log.info("Cancelling job: " + jobID);
            jobManagerClient.deleteJob(jobID);
            log.info("Job cancelled: " + jobID);
            attachments.add(SlackClient.toAttachment("Canceled job: " + jobID, warningLevel));
        } catch (final ApiException e) {
            attachments.add(SlackClient.toAttachment("Failed to cancel job: " + jobID, e));
            log.error("Failed to cancel job: " + jobID, e);
        }
    }

    // This method is only here so it can be mocked in the tests. Again the null check is to avoid the mocks
    public String getWorkerForJobType(final JobType jobType) {
        if (jobType != null)
            return jobType.getWorker();
        return "";
    }

    public RunJobResult submitNewJob(final JobType jobType) {
        Job job = null;
        try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "testLambdaHandler")) {
            final JobArgs jobArgs = new JobArgs();
            jobArgs.setService(conf.getAppMoniker());
            final String worker = getWorkerForJobType(jobType);
            if (StringUtils.isNullOrEmpty(worker)) {
                return new RunJobResult(HealthReport.TestResult.NOT_CONFIGURED_HERE, null);
            }
            jobArgs.setWorker(worker);
            final JsonObject jsonObject = new Gson().fromJson(PAYLOAD, JsonObject.class);
            jobArgs.setPayload(jsonObject);
            jobArgs.setTags(Arrays.asList(TEST_LAMBDA_JOB));

            log.info(makeString(jobType.getName(), " posting job at time=", Long.toString(new Date().getTime())));
            job = postJob(jobArgs, jobType);
            if (job == null) {
                return new RunJobResult(HealthReport.TestResult.FAILED_TO_SUBMIT, null);
            }
            submittedJobs.add(job.getJobID());
            return new RunJobResult(HealthReport.TestResult.SUCCESS, job);
        }
    }

    // This is here for mocking in tests
    public List<String> getSubmittedJobs() {
        return Collections.unmodifiableList(submittedJobs);
    }

    // This is here for mocking in tests
    public List<String> getCompletedJobs() {
        return Collections.unmodifiableList(completedJobs);
    }

    public List<String> getFoundJobs() {
        if (foundJobs == null)
            return Collections.emptyList();
        return Collections.unmodifiableList(foundJobs);
    }

    private boolean submittedJobsFound(final Set<String> recentJobs, final List<SlackMessage.Attachment> attachments) {
        final Map<Boolean, List<String>> matchingJobs = getSubmittedJobs().stream().collect(partitioningBy(recentJobs::contains));
        this.foundJobs = matchingJobs.getOrDefault(true, Collections.emptyList());
        if (getSubmittedJobs().isEmpty() || foundJobs.size() == getSubmittedJobs().size()) {
            return true;
        }
        matchingJobs.getOrDefault(false, Collections.emptyList()).stream().forEach(missingJob ->
                {
                    final String msg = makeString("Search: unable to find job ", missingJob, " in ", recentJobs.size(), " recent jobs");
                    attachments.add(SlackClient.toAttachment(msg, SlackMessage.MessageSeverity.DANGER));
                    log.error(msg.concat(makeString(": ", recentJobs)));
                }
        );
        return false;
    }

    public HealthReport.TestResult testSubmittedJobsAppearInSearch() {
        if (getSubmittedJobs().isEmpty()) {
            log.info("Search: no submitted jobs to search");
        }

        final List<Job> recentJobs = searchRecentJobs(Config.SEARCH_DURATION_SECONDS);

        // Did we get anything?
        if (recentJobs.isEmpty()) {
            slackAttachments.add(SlackClient.toAttachment("Search: unable to find any recent jobs", SlackMessage.MessageSeverity.WARNING));
            return HealthReport.TestResult.FAILED;
        }

        // The method completedJobsFound checks that our submittedJobs (one or two job IDs) are found
        // in the list of given recent jobs (also job IDs)
        log.info(makeString("Search: found ", recentJobs.size(), " recent jobs, looking for ", getCompletedJobs()));
        if (submittedJobsFound(recentJobs.stream().map(Job::getJobID).collect(toSet()), slackAttachments)) {
            final String msg = "Search: successfully found our recently submitted jobs";
            log.info(msg);
            return HealthReport.TestResult.SUCCESS;
        } else {
            return HealthReport.TestResult.FAILED;
        }
    }

    // Made public so it can be mocked
    public Map<String, List<Job>> searchRecentJobsGroupingByWorker() {
        // Add some grace period apart from max of jobs timeout seconds
        final long maxDuration = Config.SEARCH_DURATION_SECONDS + Math.max(conf.getEcsJobTimeoutSeconds(), conf.getBatchJobTimeoutSeconds());
        final List<Job> recentJobs = searchRecentJobs(maxDuration);
        // Sort jobs based on creation time from most recently to least
        return recentJobs.stream()
                .sorted(Comparator.comparing(Job::getCreationTime).reversed())
                .collect(groupingBy(Job::getWorker, toList()));
    }

    public List<Job> searchRecentJobs(final long durationSeconds) {
        final List<Job> recentJobs = new ArrayList<>();
        try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "testLambdaHandler")) {
            log.info(makeString("Search: querying for recent jobs from time=", Long.toString(new Date().getTime())));
            final DevelopersApi jobManagerClient = makeJobManagerClient(oxygenToken);
            final String service = conf.getAppMoniker();
            final Integer maxResults = 100;
            String nextToken = null;
            // Add some additional padding to toTime to account for minor time difference across system involved.
            // without this we occasionally see some recently submitted jobs missing from the result.
            final long toTime = System.currentTimeMillis() + 10_000;
            final long fromTime = toTime - (1_000 * durationSeconds);
            do {
                log.info(makeString("Search: calling searchRecentJobs to find active jobs with service=", service, ", maxResults=", maxResults, ", nextToken=", nextToken));
                final SearchResult result = jobManagerClient.searchRecentJobs(
                        service, maxResults, Long.toString(fromTime), Long.toString(toTime), TEST_LAMBDA_JOB, nextToken);
                nextToken = result.getNextToken();
                log.info(makeString("Search: query returned page with ", result.getJobs().size(), " items, nextToken(parsed)=",
                        ComputeStringOps.parseNextToken(nextToken)));
                recentJobs.addAll(result.getJobs());
            } while (!StringUtils.isNullOrEmpty(nextToken));

            // The method completedJobsFound checks that our completedJobs (one or two job IDs) are found
            // in the list of given recent jobs (also job IDs)
            log.info(makeString("Search: found ", recentJobs.size(), " recent jobs"));
        } catch (final ApiException | ComputeException e) {
            slackAttachments.add(SlackClient.toAttachment("Search: failed to find recent jobs due to error", e));
            log.error("Search: failed to find recent jobs due to error", e);
        }
        return recentJobs;
    }

    public Job postJob(final JobArgs jobArgs, final JobType jobType) {
        try {
            final DevelopersApi jobManagerClient = makeJobManagerClient(oxygenToken);
            log.info("Submitting job: {}", jobArgs);
            final Job result = jobManagerClient.createJob(jobArgs, false);
            log.info("Job submitted, id = {}", result.getJobID());
            return result;
        } catch (final ApiException e) {
            slackAttachments.add(SlackClient.toAttachment(jobType.getName() + " failed to submit", e));
            log.error(jobType.getName() + " failed to submit", e);
        }
        return null;
    }

    private boolean hasTimedOut(final Job job, final long timeoutSeconds) {
        final long creationTime = Instant.parse(job.getCreationTime()).toEpochMilli();
        return System.currentTimeMillis() - creationTime > timeoutSeconds * 1_000;
    }

    // This is separate so we can run a mock server in the tests.
    public String getBasePath() {
        return conf.getJobManagerUrl();
    }

    public DevelopersApi makeJobManagerClient(final String oxygenToken) {
        final ApiClient jmApiClient = new ApiClient();
        jmApiClient.addDefaultHeader("Authorization", "Bearer " + oxygenToken);
        jmApiClient.setBasePath(getBasePath());

        // ApiClient client internally uses OkHttpClient which allows to implement Interceptor that can be used to
        // implement retry logic Ref: https://square.github.io/okhttp/interceptors/
        final OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();

        // Copy over existing interceptors that ApiClient initializer adds
        // Ref: ApiClient.init() method
        httpClientBuilder.networkInterceptors().addAll(jmApiClient.getHttpClient().networkInterceptors());
        httpClientBuilder.interceptors().addAll(jmApiClient.getHttpClient().interceptors());

        httpClientBuilder.connectTimeout(10_000, TimeUnit.MILLISECONDS);
        httpClientBuilder.retryOnConnectionFailure(true);

        httpClientBuilder.addInterceptor(new RetryInterceptor());
        jmApiClient.setHttpClient(httpClientBuilder.build());
        return new DevelopersApi(jmApiClient);
    }

    public AmazonS3 makeS3Client() {
        final ClientConfiguration clientConfiguration = new ClientConfiguration()
                .withRetryPolicy(new RetryPolicy(
                        new AWSRetryCondition(),
                        new PredefinedBackoffStrategies.EqualJitterBackoffStrategy(500, 60_000),
                        7,
                        true));

        return AmazonS3ClientBuilder.standard().
                withClientConfiguration(clientConfiguration)
                .build();
    }

    // These uploads either succeed, or it throws.
    public void sendHealthReportToS3(final HealthReport healthReport) throws IOException {
        log.info("Sending health report to s3 job: {}", healthReport);
        final ObjectMapper objectMapper = Json.mapper;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        objectMapper.writeValue(byteArrayOutputStream, healthReport);
        final byte[] data = byteArrayOutputStream.toByteArray();

        final AmazonS3 s3client = makeS3Client();
        final ObjectMetadata metadata = new ObjectMetadata();
        // We are using security hardened module in Cosv3 to create the health bucket and
        // it explicitly disabled `Block public access (bucket settings)`, with that setting the following 'public-read'
        // give access denied.
        if (!ComputeConfig.isCosV3()) {
            metadata.setHeader("x-amz-acl", "public-read");
        }
        metadata.setHeader("Content-Length", (long) data.length);

        s3client.putObject(conf.getHealthBucket(), Config.JOB_HEALTH_LOG_FILE, new ByteArrayInputStream(data), metadata);
        s3client.putObject(conf.getHealthBucket(), conf.getHistoryLogFileName(), new ByteArrayInputStream(data), metadata);
    }
}
