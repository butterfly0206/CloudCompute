package com.autodesk.compute.model.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import java.util.concurrent.TimeUnit;

import static com.autodesk.compute.configuration.ComputeConstants.DynamoFields.*;

@Data
@NoArgsConstructor // DynamoDB requires a no-args constructor
@DynamoDBTable(tableName = IDEMPOTENT_JOBS_TABLE_NAME)
public class IdempotentJobDBRecord {
    // Keep the record in database for 2 month (jobs are kept for 1 month + max time for job to finish is about a month)
    public static final long JOB_RETENTION_TIME_SECONDS = TimeUnit.DAYS.toSeconds(60);

    // Required attributes
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBHashKey(attributeName = IDEMPOTENCY_ID)
    private String idempotencyId;
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBAttribute(attributeName = JOB_ID)
    private String jobId;
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBAttribute(attributeName = TTL)
    private Long ttl;

    @DynamoDBIgnore
    public static long getTtlSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + JOB_RETENTION_TIME_SECONDS;
    }

    public static IdempotentJobDBRecord createDefault() {
        final IdempotentJobDBRecord record = new IdempotentJobDBRecord();
        record.ttl = getTtlSeconds();
        return record;
    }
}

