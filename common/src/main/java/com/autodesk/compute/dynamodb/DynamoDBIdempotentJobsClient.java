package com.autodesk.compute.dynamodb;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.dynamodb.IdempotentJobDBRecord;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

@Slf4j
public class DynamoDBIdempotentJobsClient extends DynamoDBClient {

    private final DynamoDBMapper consistentReadDynamoDBMapper;
    private final String dynamoDbTable;

    public DynamoDBIdempotentJobsClient() {
        this(null);
    }

    public DynamoDBIdempotentJobsClient(final AmazonDynamoDB amazonDynamoDBClient) {
        this(amazonDynamoDBClient, ComputeConfig.getInstance().getIdempotentJobsTable());
    }

    public DynamoDBIdempotentJobsClient(final AmazonDynamoDB amazonDynamoDBClient, final String dynamoDbTable) {
        super(amazonDynamoDBClient);
        this.dynamoDbTable = dynamoDbTable;
        consistentReadDynamoDBMapper = buildDynamoDBMapper(dynamoDbTable, DynamoDBMapperConfig.ConsistentReads.CONSISTENT);
    }
    
    public IdempotentJobDBRecord getJob(final String idempotencyId) throws ComputeException {
        final IdempotentJobDBRecord record;
        try {
            record = consistentReadDynamoDBMapper.load(IdempotentJobDBRecord.class, idempotencyId);
        } catch (final Exception e) {
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        }
        return record;
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
