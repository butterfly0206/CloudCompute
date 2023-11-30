package com.autodesk.compute.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.model.dynamodb.AppUpdateMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DynamoDBSnsClient extends DynamoDBClient {

    private final DynamoDBMapper dynamoDBMapper;

    public DynamoDBSnsClient() {
        super();
        dynamoDBMapper = buildDynamoDBMapper(ComputeConfig.getInstance().getDbSnsTable());
    }

    public void update(final AppUpdateMapper update) {
        dynamoDBMapper.save(update);
    }
}
