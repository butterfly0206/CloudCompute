package com.autodesk.compute.common;

import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.google.common.collect.Lists;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.autodesk.compute.configuration.ComputeConstants.MetricsNames.*;

// This class is a mock implementation of the central repository for writing custom metrics to CloudWatch.
@Slf4j
public class MockMetrics extends Metrics implements AutoCloseable {
    // Effectively constant
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<MetricDatum>> datumsByMetricName =
            new ConcurrentHashMap<>();

    protected MockMetrics() {
        this(Duration.ofSeconds(10));
    }

    protected MockMetrics(final Duration batchSendFrequency) {
        this.batchSendFrequency = batchSendFrequency;
    }

    @Override
    public void close() throws Exception {
        log.info("MockMetrics.close");
    }

    @Override
    public void start() {
        log.info("MockMetrics.start");
    }

    public Map<String, Collection<MetricDatum>> getAllCapturedMetrics() {
        return Collections.unmodifiableMap(datumsByMetricName);
    }

    @Override
    public Collection<Dimension> computeDimensions() {
        final ArrayList<Dimension> arrayList = new ArrayList<>();
        // The monikers are all upper-case by convention. This is kept consistent with the
        // code in cloudos-tf-compute at https://git.autodesk.com/cloud-platform/cloudos-tf-compute/blob/master/metrics.py#L33
        arrayList.add(new Dimension().withName(Metrics.COMPUTE_MONIKER).withValue(getComputeAppMoniker().toUpperCase()));
        return arrayList;
    }

    @Override
    public Collection<Dimension> monikerDimensions(final String moniker) {
        final ArrayList<Dimension> arrayList = new ArrayList<>();
        // The monikers are all upper-case by convention. This is kept consistent with the
        // code in cloudos-tf-compute at https://git.autodesk.com/cloud-platform/cloudos-tf-compute/blob/master/metrics.py#L33
        arrayList.add(new Dimension().withName(Metrics.MONIKER).withValue(moniker.toUpperCase()));
        return arrayList;
    }

    @Override
    public Collection<Dimension> workerDimensions(@NonNull final String moniker, @NonNull final String workerName) {
        return Lists.newArrayList(
                new Dimension().withName(Metrics.MONIKER).withValue(moniker.toUpperCase()),
                new Dimension().withName(Metrics.WORKER_NAME).withValue(workerName));
    }

    @Override
    public void reportAsynchronousJobCreationFailed(final String moniker, final String workerName) {
        reportMockTickMetric(ASYNCHRONOUS_JOB_CREATION_FAILED, moniker, workerName);
    }

    @Override
    public void reportGetJobCall() {
        reportMockTickMetric(GET_JOB_CALL, NAMESPACE);
    }

    @Override
    public void reportHeartbeatSent() {
        reportMockTickMetric(WORKER_HEARTBEAT);
    }

    @Override
    public void reportJobSubmitted(final String moniker, final String workerName) {
        reportMockTickMetric(JOB_SUBMITTED, moniker, workerName);
    }

    @Override
    public void reportArrayJobSubmitted(final String moniker, final String workerName) {
        reportMockTickMetric(ARRAY_JOB_SUBMITTED, moniker, workerName);
    }

    @Override
    public void reportJobSubmitFailed(final String moniker, final String workerName) {
        reportMockTickMetric(JOB_SUBMIT_FAILED, moniker, workerName);
    }

    @Override
    public void reportJobScheduleDeferredOnConflict(final String moniker, final String workerName) {
        reportMockTickMetric(JOB_SCHEDULE_DEFERRED_ON_CONFLICT, moniker, workerName);
    }

    @Override
    public void reportTestJobSubmitted(final String moniker, final String workerName) {
        reportMockTickMetric(TEST_JOB_SUBMITTED, moniker, workerName);
    }

    @Override
    public void reportNoBatchJobSubmitted(final String moniker, final String workerName) {
        reportMockTickMetric(NOBATCH_JOB_SUBMITTED, moniker, workerName);
    }

    @Override
    public void reportServiceIntegrationJobSubmitted(final String moniker, final String workerName) {
        reportMockTickMetric(SERVICE_INTEGRATION_JOB_SUBMITTED, moniker, workerName);
    }

    @Override
    public void reportPollingJobSubmitted(final String moniker, final String workerName) {
        reportMockTickMetric(POLLING_JOB_SUBMITTED, moniker, workerName);
    }

    @Override
    public void reportPollingBatchJob(final String moniker, final String workerName) {
        reportMockTickMetric(POLLING_BATCH_JOB, moniker, workerName);
    }

    @Override
    public void reportJobCanceled(final String moniker, final String workerName) {
        reportMockTickMetric(JOB_CANCEL_SUCCEEDED, moniker, workerName);
    }

    @Override
    public void reportJobCancelFailed(final String moniker, final String workerName) {
        reportMockTickMetric(JOB_CANCEL_FAILED, moniker, workerName);
    }

    @Override
    public void reportJobStarted(final String moniker, final String workerName) {
        reportMockTickMetric(JOB_STARTED, moniker, workerName);
    }

    @Override
    public void reportJobDuration(final String moniker, final String workerName, final double durationSeconds) {
        reportMockDurationMetric(JOB_DURATION, moniker, workerName, durationSeconds);
    }

    @Override
    public void reportJobScheduledDuration(final String moniker, final String workerName, final double durationSeconds) {
        reportMockDurationMetric(JOB_SCHEDULED_DURATION, moniker, workerName, durationSeconds);
    }

    @Override
    public void reportAdfDataAge(final double durationSeconds) {
        reportMockDurationMetric(ADF_AGE_RETURNED, durationSeconds);
    }

    @Override
    public void reportJobSucceeded(final String moniker, final String workerName) {
        reportMockTickMetric(JOB_SUCCEEDED, moniker, workerName);
    }

    @Override
    public void reportJobFailed(final String moniker, final String workerName) {
        reportMockTickMetric(JOB_FAILED, moniker, workerName);
    }

    @Override
    public void reportSecretWritten() {
        reportMockTickMetric(SECRET_WRITTEN);
    }

    @Override
    public void reportWorkerAuthSucceeded(final String moniker) {
        reportMockTickMetric(WORKER_AUTH_SUCCEEDED, moniker);
    }

    @Override
    public void reportFallbackAuthUsed(final String moniker) {
        reportMockTickMetric(WORKER_FALLBACK_AUTH_USED, moniker);
    }

    @Override
    public void reportGatekeeperApiCall() {
        reportMockTickMetric(GATEKEEPER_API_CALL);
    }

    @Override
    public void reportWorkerAuthFailed(final String moniker) {
        reportMockTickMetric(WORKER_AUTH_FAILED, moniker);
    }

    @Override
    public void reportNoAuthHeader() {
        reportMockTickMetric(WORKER_NO_AUTH_HEADER);
    }

    @Override
    public void reportProgressSent() {
        reportMockTickMetric(WORKER_PROGRESS);
    }

    @Override
    public void reportRedisCacheError() { reportMockTickMetric(REDIS_CACHE_ERROR); }

    @Override
    public void reportRedisCacheHit() { reportMockTickMetric(REDIS_CACHE_HIT); }

    @Override
    public void reportRedisCacheMiss() { reportMockTickMetric(REDIS_CACHE_MISS); }

    @Override
    public void reportRedisWrite() { reportMockTickMetric(REDIS_CACHE_WRITE); }

    @Override
    public void reportRedisCacheEntryNotFound() { reportMockTickMetric(REDIS_CACHE_ENTRY_NOT_FOUND); }

    @Override
    public void reportRedisCacheCleared() { reportMockTickMetric(REDIS_CACHE_CLEARED); }

    @Override
    public void reportCreateBatchJobGatherSIDuration(final double durationSeconds) {
        reportMockDurationMetric(CREATE_BATCH_JOB_GATHER_SI, durationSeconds);
    }

    @Override
    public void reportCreateBatchJobSubmitDuration(final double durationSeconds) {
        reportMockDurationMetric(CREATE_BATCH_JOB_SUBMIT, durationSeconds);
    }

    @Override
    public void reportCreateJobUpdateMaxRunningJobCountDuration(final double durationSeconds) {
        reportMockDurationMetric(CREATE_JOB_UPDATE_MAX_RUNNING_JOB_COUNT, durationSeconds);
    }

    @Override
    public void reportCreateJobCheckForThrottlingDuration(final double durationSeconds) {
        reportMockDurationMetric(CREATE_JOB_CHECK_FOR_THROTTLING, durationSeconds);
    }

    @Override
    public void reportCreateJobGetWorkerResourcesDuration(final double durationSeconds) {
        reportMockDurationMetric(CREATE_JOB_GET_WORKER_RESOURCES, durationSeconds);
    }

    @Override
    public void reportCreateJobInsertDBRecordDuration(final double durationSeconds) {
        reportMockDurationMetric(CREATE_JOB_INSERT_DB_RECORD, durationSeconds);
    }

    @Override
    public void reportCreateJobCreateBatchJobDuration(final double durationSeconds) {
        reportMockDurationMetric(CREATE_JOB_CREATE_BATCH_JOB, durationSeconds);
    }

    @Override
    public void reportCreateJobAddStepExecutionArnDuration(final double durationSeconds) {
        reportMockDurationMetric(CREATE_JOB_ADD_STEP_EXECUTION_ARN, durationSeconds);
    }

    @Override
    public void reportRefreshJobCacheDuration(final double durationSeconds) {
        reportMockDurationMetric(REFRESH_JOB_CACHE, durationSeconds);
    }

    @Override
    public void reportGetJobFromCacheDuration(final double durationSeconds) {
        reportMockDurationMetric(GET_JOB_FROM_CACHE, durationSeconds);
    }

    private void reportMockTickMetric(final String metricName) {
        final MetricDatum computeDimensionedDatum = new MetricDatum()
                .withDimensions(computeDimensions())
                .withMetricName(metricName)
                .withUnit(StandardUnit.None)
                .withTimestamp(Date.from(Instant.now()))
                .withValue(1.0);
        insertMockData(metricName, computeDimensionedDatum);
    }

    private void reportMockTickMetric(final String metricName, final String moniker) {
        reportMockTickMetric(metricName);
        final MetricDatum monikerDimensionedDatum = new MetricDatum()
                .withDimensions(monikerDimensions(moniker))
                .withMetricName(metricName)
                .withUnit(StandardUnit.None)
                .withTimestamp(Date.from(Instant.now()))
                .withValue(1.0);
        insertMockData(metricName, monikerDimensionedDatum);
    }

    private void reportMockTickMetric(final String metricName, final String moniker, final String workerName) {
        // Also send the undimensioned version
        reportMockTickMetric(metricName);
        final MetricDatum datum = new MetricDatum()
                .withMetricName(metricName)
                .withUnit(StandardUnit.None)
                .withDimensions(workerDimensions(moniker, workerName))
                .withTimestamp(Date.from(Instant.now()))
                .withValue(1.0);
        insertMockData(metricName, datum);
    }

    private void reportMockDurationMetric(final String metricName, final String moniker, final String workerName, final double durationSeconds) {
        final MetricDatum datum = new MetricDatum()
                .withMetricName(metricName)
                .withUnit(StandardUnit.Seconds)
                .withDimensions(workerDimensions(moniker, workerName))
                .withTimestamp(Date.from(Instant.now()))
                .withValue(durationSeconds);
        insertMockData(metricName, datum);
        reportMockDurationMetric(metricName, durationSeconds);
    }

    private void reportMockDurationMetric(final String metricName, final double durationSeconds) {
        final MetricDatum computeDimensionedDatum = new MetricDatum()
                .withDimensions(computeDimensions())
                .withMetricName(metricName)
                .withUnit(StandardUnit.Seconds)
                .withTimestamp(Date.from(Instant.now()))
                .withValue(durationSeconds);
        insertMockData(metricName, computeDimensionedDatum);
    }

    private void insertMockData(@NonNull final String metricName, @NonNull final MetricDatum ...data) {
        final ConcurrentLinkedQueue<MetricDatum> datums = datumsByMetricName.computeIfAbsent(metricName, key -> new ConcurrentLinkedQueue<>());
        Arrays.stream(data).filter(Objects::nonNull).forEach(datums::add);
    }
}

