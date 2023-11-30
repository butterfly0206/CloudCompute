package com.autodesk.compute.dynamodb;

public interface IDynamoDBJobClientConfig {
    String getDbProgressTable();

    String getDbProgressTableSearchIndex();

    String getDbProgressTableQueueingIndex();

    String getDbProgressTableArrayJobIndex();

    String getIdempotentJobsTable();

    String getActiveJobsCountTable();
}