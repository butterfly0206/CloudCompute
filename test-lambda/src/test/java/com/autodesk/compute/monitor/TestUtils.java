package com.autodesk.compute.monitor;

import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.model.Job;
import com.autodesk.compute.common.model.Status;
import com.autodesk.compute.common.model.StatusUpdate;
import com.autodesk.compute.model.HealthReport;
import com.autodesk.compute.model.RunJobResult;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@UtilityClass
public class TestUtils {

    public static AtomicInteger counter = new AtomicInteger(0);

    public CompletableFuture<RunJobResult> doneImmediately() {
        return CompletableFuture.completedFuture(
                new RunJobResult(
                        HealthReport.TestResult.SUCCESS,
                        makeMockJob(null)
                )
        );
    }

    // Since the Job class is generated, we don't have a builder class for it, so we have to do stuff
    // like this. Bleah.
    public Job makeMockJob(final String worker, final Status currentStatus, final int jobAgeSeconds, final boolean includeQueuedInHistory) {
        final Job job = new Job();
        job.setStatus(currentStatus);
        job.setJobID(Integer.toString(counter.incrementAndGet()));
        job.setStatusUpdates(Collections.emptyList());
        job.setTags(Collections.emptyList());
        job.setResult(job.getJobID());
        job.setService("fpccomp-c-uw2-sb");
        job.setWorker(ComputeStringOps.isNullOrEmpty(worker) ? "mock-worker" : worker);
        job.setPortfolioVersion("1.0.0");
        job.setErrors(Collections.emptyList());
        job.setPayload(job.getResult());
        final ArrayList<StatusUpdate> statuses = new ArrayList<>();
        if (includeQueuedInHistory) {
            statuses.add(makeStatusUpdate(Status.QUEUED, System.currentTimeMillis() - 8_000));
        }
        // Using the ordinals in general is sinning, but quite convenient here...
        if (currentStatus.ordinal() >= Status.INPROGRESS.ordinal()) {
            statuses.add(makeStatusUpdate(Status.INPROGRESS, System.currentTimeMillis() - 5_000));
        }
        if (currentStatus == Status.COMPLETED || currentStatus == Status.FAILED || currentStatus == Status.CANCELED)
        {
            statuses.add(makeStatusUpdate(currentStatus, System.currentTimeMillis() - 2_000));
        }
        job.setStatusUpdates(statuses);
        job.setCreationTime(
                Instant.ofEpochMilli(System.currentTimeMillis() - jobAgeSeconds * 1_000).atZone(ZoneOffset.UTC).toString());
        return job;
    }

    public Job makeMockJob(final String worker, final Status currentStatus, final boolean includeQueuedInHistory) {
        return makeMockJob(worker, currentStatus, 10, includeQueuedInHistory);
    }

    public Job makeMockJob(final String worker, final Status currentStatus) {
        return makeMockJob(worker, currentStatus, false);
    }

    public Job makeMockJob(final String worker) {
        return makeMockJob(worker, Status.COMPLETED);
    }

    public StatusUpdate makeStatusUpdate(final Status status, final long timestampMillis) {
        final StatusUpdate result = new StatusUpdate();
        result.setStatus(status);
        result.setTimestamp(Instant.ofEpochMilli(timestampMillis).atZone(ZoneOffset.UTC).toString());
        return result;
    }
}
