package com.autodesk.compute.util;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.google.common.cache.CacheLoader;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Slf4j
public class JobCacheLoader extends CacheLoader<String, JobDBRecord> {

    private final DynamoDBJobClient dynamoDBJobClient;

    public JobCacheLoader(final DynamoDBJobClient dynamoDBJobClient) {
        this.dynamoDBJobClient = dynamoDBJobClient;
    }

    @Nullable
    @Override
    public ListenableFuture<JobDBRecord> reload(@NonNull final String key, @NonNull final JobDBRecord oldValue) throws ComputeException {
        if (Optional.ofNullable(oldValue)
                .map(JobDBRecord::getStatus)
                .map(jobStatus -> {
                    try {
                        return JobStatus.valueOf(jobStatus);
                    } catch (final IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(JobStatus::isTerminalState)
                .isPresent()) {
            return Futures.immediateFuture(oldValue);
        }

        return Futures.immediateFuture(this.load(key));
    }

    @Nullable
    @Override
    public JobDBRecord load(@NonNull final String jobId) throws ComputeException {
        return getJobFromDynamoDB(jobId);
    }

    @Nonnull
    private JobDBRecord getJobFromDynamoDB(@NonNull final String jobId) throws ComputeException {
        final JobDBRecord record = dynamoDBJobClient.getJob(jobId);
        if (record == null) {
            throw new ComputeException(ComputeErrorCodes.NOT_FOUND, makeString("Job ", jobId, " not found"));
        }
        return record;
    }
}