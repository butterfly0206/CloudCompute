package com.autodesk.compute.common;

// MAINTENANCE NOTE:
// This class has a rigid structure that is enforced by its tests -- see TestMetrics.java. If you are
// adding a new metric here, name it reportSomething(),
// and make sure that if it has dimensions for moniker and worker, and that those are
// the first two arguments. If it has a value other than 1.0 (we call those tick metrics here -- we
// use them for keeping counts), then the value needs to be its final argument.
// The tests look for these patterns and attempt to call every method in here that starts with 'report'.

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;
import com.autodesk.compute.configuration.ComputeConfig;
import com.google.common.collect.Lists;
import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.configuration.ComputeConstants.MetricsNames.*;
import static org.apache.commons.lang3.StringUtils.firstNonEmpty;

// This class is a central repository for writing custom metrics to CloudWatch.
// Internally, it makes metrics calls every 10 seconds.
@Slf4j
public class Metrics implements AutoCloseable {
    protected static final String NAMESPACE = "fpccomp";
    protected static final String MONIKER = "Moniker";
    protected static final String WORKER_NAME = "Worker";
    protected static final String COMPUTE_MONIKER = "ComputeMoniker";

    // Initializables
    protected Duration batchSendFrequency;
    private ScheduledFuture<?> flushFuture;
    private StatsDClient statsd;
    private String computeAppMoniker;

    // Effectively constant
    private final AmazonCloudWatch cw;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<MetricDatum>> datumsInNamespaces =
            new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
    private static class LazyMetricsHolder {    //NOPMD
        public static final Metrics INSTANCE = makeInstance();
        private static Metrics makeInstance() {
            final Metrics uninitialized = Metrics.getUninitializedInstance();
            uninitialized.start();
            // Capture the final variable
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    uninitialized.close();
                } catch (final Exception e) {
                    // We're exiting; no point in logging it.
                }
            }));
            return uninitialized;
        }
    }

    public static Metrics getInstance() {
        return LazyMetricsHolder.INSTANCE;
    }

    // TEST ONLY! Do not call this directly in real applications!!
    public static Metrics getUninitializedInstance() {
        // Return either a MockMetrics or a real one,
        // depending on the USE_MOCK_METRICS environment variable.
        final boolean useMock = Boolean.parseBoolean(firstNonEmpty(System.getenv(USE_MOCK_ENV_VAR), "false"));
        return useMock ? new MockMetrics() : new Metrics();
    }

    @Inject
    protected Metrics() {
        this(makeStandardClient(AmazonCloudWatchClientBuilder.standard()), Duration.ofSeconds(10));
    }

    protected Metrics(final AmazonCloudWatch cw, final Duration batchSendFrequency) {
        this.cw = cw;
        this.batchSendFrequency = batchSendFrequency;
        this.statsd = null;
        this.computeAppMoniker = null;
    }

    protected String getComputeAppMoniker() {
        if (this.computeAppMoniker == null) {
            this.computeAppMoniker = ComputeUtils.firstSupplier(
                    () -> System.getenv("APP_MONIKER"),
                    () -> ComputeConfig.getInstance().getAppMoniker());
        }
        return this.computeAppMoniker;
    }

    protected StatsDClient getStatsdClient() {
        if (this.statsd == null) {
            final String statsdUri = ComputeUtils.firstSupplier(
                    () -> System.getenv("STATSD_URI"),
                    () -> ComputeConfig.getInstance().getStatsdURI());
            final Integer statsdPort = ComputeUtils.firstSupplier(
                    () -> ((System.getenv("STATSD_PORT") != null)
                            ? Integer.parseInt(System.getenv("STATSD_PORT"))
                            : null),
                    () -> ComputeConfig.getInstance().getStatsdPort());
            this.statsd = (statsdUri != null && statsdPort != null)
                    ? new NonBlockingStatsDClient(NAMESPACE, statsdUri, statsdPort)
                    : null;
        }
        return this.statsd;
    }


    @Override
    public void close() throws Exception {
        if (flushFuture != null) {
            flushFuture.cancel(false);
        }
        // Make certain the ones remaining are sent also
        flushMetrics();
    }

    public void start() {
        if (flushFuture == null) {
            flushFuture = scheduler.scheduleWithFixedDelay(this::flushMetrics,
                    batchSendFrequency.getSeconds(), batchSendFrequency.getSeconds(), TimeUnit.SECONDS);
        } else {
            log.warn("Metrics.start: called twice; skipping second attempt to schedule the flush thread");
        }
    }

    public Collection<Dimension> computeDimensions() {
        final ArrayList<Dimension> arrayList = new ArrayList<>();
        // The monikers are all upper-case by convention. This is kept consistent with the
        // code in cloudos-tf-compute at https://git.autodesk.com/cloud-platform/cloudos-tf-compute/blob/master/metrics.py#L33
        arrayList.add(new Dimension().withName(COMPUTE_MONIKER).withValue(getComputeAppMoniker().toUpperCase()));
        return arrayList;
    }

    public Collection<Dimension> monikerDimensions(final String moniker) {
        final ArrayList<Dimension> arrayList = new ArrayList<>();
        // The monikers are all upper-case by convention. This is kept consistent with the
        // code in cloudos-tf-compute at https://git.autodesk.com/cloud-platform/cloudos-tf-compute/blob/master/metrics.py#L33
        arrayList.add(new Dimension().withName(MONIKER).withValue(moniker.toUpperCase()));
        return arrayList;
    }

    public Collection<Dimension> workerDimensions(@NonNull final String moniker, @NonNull final String workerName) {
        return Lists.newArrayList(
                new Dimension().withName(MONIKER).withValue(moniker.toUpperCase()),
                new Dimension().withName(WORKER_NAME).withValue(workerName));
    }

    public void reportAsynchronousJobCreationFailed(final String moniker, final String workerName) {
        reportTickMetric(ASYNCHRONOUS_JOB_CREATION_FAILED, moniker, workerName, NAMESPACE);
    }

    public void reportGetJobCall() {
        reportTickMetric(GET_JOB_CALL, NAMESPACE);
    }

    public void reportHeartbeatSent() {
        reportTickMetric(WORKER_HEARTBEAT, NAMESPACE);
    }

    public void reportJobSubmitted(final String moniker, final String workerName) {
        reportTickMetric(JOB_SUBMITTED, moniker, workerName, NAMESPACE);
    }

    public void reportArrayJobSubmitted(final String moniker, final String workerName) {
        reportTickMetric(ARRAY_JOB_SUBMITTED, moniker, workerName, NAMESPACE);
    }

    public void reportJobSubmitFailed(final String moniker, final String workerName) {
        reportTickMetric(JOB_SUBMIT_FAILED, moniker, workerName, NAMESPACE);
    }

    public void reportJobScheduleDeferredOnConflict(final String moniker, final String workerName) {
        reportTickMetric(JOB_SCHEDULE_DEFERRED_ON_CONFLICT, moniker, workerName, NAMESPACE);
    }

    public void reportTestJobSubmitted(final String moniker, final String workerName) {
        reportTickMetric(TEST_JOB_SUBMITTED, moniker, workerName, NAMESPACE);
    }

    public void reportNoBatchJobSubmitted(final String moniker, final String workerName) {
        reportTickMetric(NOBATCH_JOB_SUBMITTED, moniker, workerName, NAMESPACE);
    }

    public void reportServiceIntegrationJobSubmitted(final String moniker, final String workerName) {
        reportTickMetric(SERVICE_INTEGRATION_JOB_SUBMITTED, moniker, workerName, NAMESPACE);
    }

    public void reportPollingJobSubmitted(final String moniker, final String workerName) {
        reportTickMetric(POLLING_JOB_SUBMITTED, moniker, workerName, NAMESPACE);
    }

    public void reportPollingBatchJob(final String moniker, final String workerName) {
        reportTickMetric(POLLING_BATCH_JOB, moniker, workerName, NAMESPACE);
    }

    public void reportJobCanceled(final String moniker, final String workerName) {
        reportTickMetric(JOB_CANCEL_SUCCEEDED, moniker, workerName, NAMESPACE);
    }

    public void reportJobCancelFailed(final String moniker, final String workerName) {
        reportTickMetric(JOB_CANCEL_FAILED, moniker, workerName, NAMESPACE);
    }

    public void reportJobStarted(final String moniker, final String workerName) {
        reportTickMetric(JOB_STARTED, moniker, workerName, NAMESPACE);
    }

    public void reportJobDuration(final String moniker, final String workerName, final double durationSeconds) {
        reportDurationMetric(JOB_DURATION, moniker, workerName, NAMESPACE, durationSeconds);
    }

    public void reportJobScheduledDuration(final String moniker, final String workerName, final double durationSeconds) {
        reportDurationMetric(JOB_SCHEDULED_DURATION, moniker, workerName, NAMESPACE, durationSeconds);
    }

    public void reportAdfDataAge(final double durationSeconds) {
        reportDurationMetric(ADF_AGE_RETURNED, NAMESPACE, durationSeconds);
    }

    public void reportJobSucceeded(final String moniker, final String workerName) {
        reportTickMetric(JOB_SUCCEEDED, moniker, workerName, NAMESPACE);
    }

    public void reportJobFailed(final String moniker, final String workerName) {
        reportTickMetric(JOB_FAILED, moniker, workerName, NAMESPACE);
    }

    public void reportSecretWritten() {
        reportTickMetric(SECRET_WRITTEN, NAMESPACE);
    }

    public void reportWorkerAuthSucceeded(final String moniker) {
        reportTickMetric(WORKER_AUTH_SUCCEEDED, moniker, NAMESPACE);
    }

    public void reportFallbackAuthUsed(final String moniker) {
        reportTickMetric(WORKER_FALLBACK_AUTH_USED, moniker, NAMESPACE);
    }

    public void reportGatekeeperApiCall() {
        reportTickMetric(GATEKEEPER_API_CALL, NAMESPACE);
    }

    public void reportWorkerAuthFailed(final String moniker) {
        reportTickMetric(WORKER_AUTH_FAILED, moniker, NAMESPACE);
    }

    public void reportNoAuthHeader() {
        reportTickMetric(WORKER_NO_AUTH_HEADER, NAMESPACE);
    }

    public void reportProgressSent() {
        reportTickMetric(WORKER_PROGRESS, NAMESPACE);
    }

    public void reportRedisCacheError() { reportTickMetric(REDIS_CACHE_ERROR, NAMESPACE); }

    public void reportRedisCacheHit() { reportTickMetric(REDIS_CACHE_HIT, NAMESPACE); }

    public void reportRedisCacheMiss() { reportTickMetric(REDIS_CACHE_MISS, NAMESPACE); }

    public void reportRedisWrite() { reportTickMetric(REDIS_CACHE_WRITE, NAMESPACE); }

    public void reportRedisCacheEntryNotFound() { reportTickMetric(REDIS_CACHE_ENTRY_NOT_FOUND, NAMESPACE); }

    public void reportRedisCacheCleared() { reportTickMetric(REDIS_CACHE_CLEARED, NAMESPACE); }

    public void reportCreateBatchJobGatherSIDuration(final double durationSeconds) {
        reportDurationMetric(CREATE_BATCH_JOB_GATHER_SI, NAMESPACE, durationSeconds);
    }

    public void reportCreateBatchJobSubmitDuration(final double durationSeconds) {
        reportDurationMetric(CREATE_BATCH_JOB_SUBMIT, NAMESPACE, durationSeconds);
    }

    public void reportCreateJobUpdateMaxRunningJobCountDuration(final double durationSeconds) {
        reportDurationMetric(CREATE_JOB_UPDATE_MAX_RUNNING_JOB_COUNT, NAMESPACE, durationSeconds);
    }

    public void reportCreateJobCheckForThrottlingDuration(final double durationSeconds) {
        reportDurationMetric(CREATE_JOB_CHECK_FOR_THROTTLING, NAMESPACE, durationSeconds);
    }

    public void reportCreateJobGetWorkerResourcesDuration(final double durationSeconds) {
        reportDurationMetric(CREATE_JOB_GET_WORKER_RESOURCES, NAMESPACE, durationSeconds);
    }

    public void reportCreateJobInsertDBRecordDuration(final double durationSeconds) {
        reportDurationMetric(CREATE_JOB_INSERT_DB_RECORD, NAMESPACE, durationSeconds);
    }

    public void reportCreateJobCreateBatchJobDuration(final double durationSeconds) {
        reportDurationMetric(CREATE_JOB_CREATE_BATCH_JOB, NAMESPACE, durationSeconds);
    }

    public void reportCreateJobAddStepExecutionArnDuration(final double durationSeconds) {
        reportDurationMetric(CREATE_JOB_ADD_STEP_EXECUTION_ARN, NAMESPACE, durationSeconds);
    }

    public void reportRefreshJobCacheDuration(final double durationSeconds) {
        reportDurationMetric(REFRESH_JOB_CACHE, NAMESPACE, durationSeconds);
    }

    public void reportGetJobFromCacheDuration(final double durationSeconds) {
        reportDurationMetric(GET_JOB_FROM_CACHE, NAMESPACE, durationSeconds);
    }

    private void reportTickMetric(final String metricName, final String namespace) {
        final MetricDatum computeDimensionedDatum = new MetricDatum()
                .withDimensions(computeDimensions())
                .withMetricName(metricName)
                .withUnit(StandardUnit.None)
                .withTimestamp(Date.from(Instant.now()))
                .withValue(1.0);
        insertData(namespace, computeDimensionedDatum);
    }

    private void reportTickMetric(final String metricName, final String moniker, final String namespace) {
        reportTickMetric(metricName, namespace);
        final MetricDatum monikerDimensionedDatum = new MetricDatum()
                .withDimensions(monikerDimensions(moniker))
                .withMetricName(metricName)
                .withUnit(StandardUnit.None)
                .withTimestamp(Date.from(Instant.now()))
                .withValue(1.0);
        insertData(namespace, monikerDimensionedDatum);
    }

    private void reportTickMetric(final String metricName, final String moniker, final String workerName, final String namespace) {
        // Also send the undimensioned version
        reportTickMetric(metricName, namespace);
        final MetricDatum datum = new MetricDatum()
                .withMetricName(metricName)
                .withUnit(StandardUnit.None)
                .withDimensions(workerDimensions(moniker, workerName))
                .withTimestamp(Date.from(Instant.now()))
                .withValue(1.0);
        insertData(namespace, datum);
    }

    private void reportDurationMetric(final String metricName, final String moniker, final String workerName, final String namespace, final double durationSeconds) {
        final MetricDatum datum = new MetricDatum()
                .withMetricName(metricName)
                .withUnit(StandardUnit.Seconds)
                .withDimensions(workerDimensions(moniker, workerName))
                .withTimestamp(Date.from(Instant.now()))
                .withValue(durationSeconds);
        insertData(namespace, datum);
        reportDurationMetric(metricName, namespace, durationSeconds);
    }

    private void reportDurationMetric(final String metricName, final String namespace, final double durationSeconds) {
        final MetricDatum computeDimensionedDatum = new MetricDatum()
                .withDimensions(computeDimensions())
                .withMetricName(metricName)
                .withUnit(StandardUnit.Seconds)
                .withTimestamp(Date.from(Instant.now()))
                .withValue(durationSeconds);
        insertData(namespace, computeDimensionedDatum);
    }

    private void insertData(@NonNull final String namespace, @NonNull final MetricDatum... data) {
        final ConcurrentLinkedQueue<MetricDatum> datums = datumsInNamespaces.computeIfAbsent(namespace, key -> new ConcurrentLinkedQueue<>());
        Arrays.stream(data).filter(Objects::nonNull).forEach(datums::add);
    }

    private void flushMetrics() {
        final String[] namespaces = datumsInNamespaces.keySet().toArray(new String[0]);
        // Carefully remove the existing collections so as not to lose anything in a race condition...
        for (final String namespace : namespaces) {
            final ConcurrentLinkedQueue<MetricDatum> current = datumsInNamespaces.remove(namespace);
            if (!current.isEmpty()) {
                putMetrics(namespace, current);
            }
        }
    }

    private void putMetrics(final String namespace, final Collection<MetricDatum> metrics) {
        // Break the collection of metrics into batches of 20 - the maximum allowed by PutMetricData.
        // https://docs.aws.amazon.com/AmazonCloudWatch/latest/APIReference/API_PutMetricData.html
        log.info("Metrics.putMetrics: input contains " + metrics.size() + " metrics to publish");
        final List<List<MetricDatum>> batchedRequests = Lists.partition(new ArrayList<>(metrics), 20);
        for (final List<MetricDatum> oneBatch : batchedRequests) {
            final PutMetricDataRequest request = new PutMetricDataRequest()
                    .withNamespace(namespace)
                    .withMetricData(oneBatch);
            final PutMetricDataResult result = cw.putMetricData(request);
            if (result.getSdkHttpMetadata().getHttpStatusCode() != 200) {
                log.error("Metrics.putMetrics: flushing metrics aws request id {} failed with HTTP status {}. {} metrics lost!",
                        result.getSdkResponseMetadata().getRequestId(), result.getSdkHttpMetadata().getHttpStatusCode(), oneBatch.size());
            }
        }
        if (getStatsdClient() != null) {
            final Map<Boolean, List<MetricDatum>> metricPerType = batchedRequests
                    .stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.partitioningBy(cwData -> StandardUnit.Seconds.equals(cwData.getUnit())));

            metricPerType.get(true)
                    .forEach(
                            metric -> getStatsdClient().recordExecutionTime(statsdMetricFromCloudwatchMetric(metric), metric.getValue().longValue())
                    );

            metricPerType.get(false)
                    .forEach(
                            metric -> getStatsdClient().count(statsdMetricFromCloudwatchMetric(metric), metric.getValue().longValue())
                    );
        }
    }

    private String statsdMetricFromCloudwatchMetric(final MetricDatum metric) {
        return String.join(".", metric.getDimensions()
                .stream().map(Dimension::getValue)
                .map(String::toLowerCase)
                .collect(Collectors.toList())) + "."
                + metric.getMetricName().toLowerCase();
    }

}

