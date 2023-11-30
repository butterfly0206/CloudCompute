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
@DynamoDBTable(tableName = ACTIVE_JOBS_COUNT_TABLE_NAME)
public class ActiveJobsCountDBRecord {

    public static final long RECORD_RETENTION_TIME_SECONDS = TimeUnit.DAYS.toSeconds(30);

    // Required attributes
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBHashKey(attributeName = USER_SERVICE_WORKER)
    private String userServiceWorker;
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBAttribute(attributeName = COUNT)
    private int count;
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBAttribute(attributeName = TTL)
    private Long ttl;

    @DynamoDBIgnore
    public static long getTtlSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + RECORD_RETENTION_TIME_SECONDS;
    }

    public static ActiveJobsCountDBRecord createDefault() {
        final ActiveJobsCountDBRecord record = new ActiveJobsCountDBRecord();
        record.ttl = getTtlSeconds();
        return record;
    }
}

