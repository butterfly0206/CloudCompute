package com.autodesk.compute.dynamodb;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.dynamodb.ActiveJobsCountDBRecord;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Slf4j
public class DynamoDBActiveJobsCountClient extends DynamoDBClient {

    private final DynamoDBMapper consistentReadDynamoDBMapper;
    private final String dynamoDbTable;

    public DynamoDBActiveJobsCountClient() {
        this((AmazonDynamoDB) null);
    }

    public DynamoDBActiveJobsCountClient(final AmazonDynamoDB amazonDynamoDBClient) {
        super(amazonDynamoDBClient);
        final IDynamoDBJobClientConfig computeConfig = getComputeConfig();
        this.dynamoDbTable = computeConfig.getActiveJobsCountTable();
        consistentReadDynamoDBMapper = buildDynamoDBMapper(dynamoDbTable, DynamoDBMapperConfig.ConsistentReads.CONSISTENT);
    }

    public void insertZero(final String userServiceWorker) throws ComputeException {
        try {
            final ActiveJobsCountDBRecord record = ActiveJobsCountDBRecord.createDefault();
            record.setUserServiceWorker(userServiceWorker);
            record.setCount(0);
            consistentReadDynamoDBMapper.save(record);
        } catch (final Exception e) {
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        }
    }

    public int getCount(final String userServiceWorker) throws ComputeException {
        final ActiveJobsCountDBRecord record;
        try {
            record = consistentReadDynamoDBMapper.load(ActiveJobsCountDBRecord.class, userServiceWorker);
            // There is no record, initialized it to 0
            if (record == null) {
                insertZero(userServiceWorker);
                return 0;
            }
        } catch (final Exception e) {
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        }
        return record.getCount();
    }

    public BigDecimal getItemCount() throws ComputeException {
        try {
            final Table table = dynamoDB.getTable(dynamoDbTable);
            return new BigDecimal(table.describe().getItemCount());
        } catch (final AmazonClientException e) {
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        }
    }
}
