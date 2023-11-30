package com.autodesk.compute.dynamodb;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkBaseException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.util.StringUtils;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.Failure;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.dynamodb.*;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.collect.ImmutableMap;
import jakarta.inject.Inject;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.autodesk.compute.common.ComputeStringOps.*;
import static com.autodesk.compute.configuration.ComputeConstants.DynamoFields.*;
import static com.autodesk.compute.model.dynamodb.JobDBRecord.*;
import static com.google.common.base.MoreObjects.firstNonNull;


@Slf4j
public class DynamoDBJobClient extends DynamoDBClient {

    public static final String NEXT_TOKEN_FIELD = "nextToken";
    public static final String JOBS_FIELD = "jobs";
    public static final String ERROR_FIELD = "error";
    private static final String CAN_T_PERFORM_UPDATE_ON_RECORED = "Can't perform update on the record ";
    private static final String FAILED_TO_UPDATE_RECORD = "Failed to update the record ";
    private static final String UNEXPECTED_ERROR_WHILE_UPDATING_RECORD = "Unexpected error while updating the record ";
    public static final String JOB_ID_MUST_EXIST = "JobId must exist";
    private static final String CONDITIONAL_CHECK_QINFO_V_V = "#qinfo.#v = :v";
    public static final String EMPTY_LIST = ":empty_list";
    public static final String SEARN_FIELD = ":searn";
    public static final String SEARN_VALUE = "#searn";
    private static final Random random = new Random();

    private final DynamoDBMapper dynamoDbMapper;
    private final DynamoDBMapper consistentReadDynamoDBMapper;
    private final DynamoDBMapper dynamoDbTransactionMapper;

    private final String dynamoDbJobsTable;
    private final String dynamoDbSearchIndex;
    private final String dynamoDbQueueingIndex;
    private final String dynamoDbArrayJobIndex;
    private final String dynamoDbIdempotencyJobsTable;
    private final String dynamoDbActiveJobsCountTable;

    public enum HeartbeatStatus {
        ENABLED("ENABLED"),
        DISABLED("DISABLED");

        private final String value;

        HeartbeatStatus(final String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }
    }

    private static String getJobStatusTimestampString(final JobStatus jobStatus) {
        return getJobStatusUpdateAsString(jobStatus.toString());
    }

    @Inject
    public DynamoDBJobClient() {
        this(ComputeConfig.getInstance(), null, null);
    }

    public DynamoDBJobClient(final AmazonDynamoDB amazonDynamoDBClient) {
        this(ComputeConfig.getInstance(), amazonDynamoDBClient, null);
    }

    public DynamoDBJobClient(final IDynamoDBJobClientConfig config,
                             final AmazonDynamoDB amazonDynamoDB,
                             final String argDynamoDbJobsTable) {
        super(amazonDynamoDB);
        this.dynamoDbJobsTable = firstNonNull(argDynamoDbJobsTable, config.getDbProgressTable());
        this.dynamoDbSearchIndex = config.getDbProgressTableSearchIndex();
        this.dynamoDbQueueingIndex = config.getDbProgressTableQueueingIndex();
        this.dynamoDbIdempotencyJobsTable = config.getIdempotentJobsTable();
        this.dynamoDbActiveJobsCountTable = config.getActiveJobsCountTable();
        this.dynamoDbArrayJobIndex = config.getDbProgressTableArrayJobIndex();
        dynamoDbMapper = buildDynamoDBMapper(this.dynamoDbJobsTable);
        consistentReadDynamoDBMapper = buildDynamoDBMapper(this.dynamoDbJobsTable, DynamoDBMapperConfig.ConsistentReads.CONSISTENT);
        dynamoDbTransactionMapper = buildDynamoDBMapper(new TableNameResolver(config));
    }

    public DynamoDBJobClient(final IDynamoDBJobClientConfig config) {
        this(config, null, null);
    }

    // For Tests to mock
    public DynamoDB getDynamoDB() {
        return this.dynamoDB;
    }

    // For Tests to mock
    public AmazonDynamoDB getAmazonDynamoDB() {
        return this.amazonDynamoDB;
    }

    // For Tests to mock
    public DynamoDBMapper getProgressTableDbMapper() {
        return dynamoDbMapper;
    }

    private <T> boolean trueIfInvalid(final String fieldName, final T fieldValue) {
        if (fieldValue == null) {
            log.warn("Field {} is null, but probably shouldn't be", fieldName);
            return true;
        }
        return false;
    }

    // Double check things that must be non-null
    private boolean validateNonNull(final JobDBRecord jobRecord) {
        boolean result = trueIfInvalid("resolvedPortfolioVersion", jobRecord.getResolvedPortfolioVersion());
        result |= trueIfInvalid("service", jobRecord.getService());
        result |= trueIfInvalid("worker", jobRecord.getWorker());
        result |= trueIfInvalid("jobId", jobRecord.getJobId());
        result |= trueIfInvalid("status", jobRecord.getStatus());
        result |= trueIfInvalid("ttl", jobRecord.getTtl());
        if (result) {
            log.warn("One of the fields in job record {} looks unhealthy!",
                    jobRecord.getJobId());
        }
        return result;
    }

    public JobDBRecord insertJob(final JobDBRecord jobRecord) throws ComputeException {
        try {
            // Set ttl
            jobRecord.setTtl(fillJobTtlSeconds());
            if (!jobRecord.initQueueInfo(jobRecord.getUserServiceWorkerId())) {
                jobRecord.setStatus(JobStatus.SCHEDULED.toString());
                final List<String> statusUpdate = Arrays.asList(getJobStatusTimestampString(JobStatus.SCHEDULED));
                jobRecord.setStatusUpdates(statusUpdate);
            }
            validateNonNull(jobRecord);
            if (StringUtils.isNullOrEmpty(jobRecord.getIdempotencyId())) {
                getProgressTableDbMapper().save(jobRecord);
            } else {
                // Do a transaction on 2 tables to store job and idempotency
                final IdempotentJobDBRecord idemRecord = IdempotentJobDBRecord.createDefault();
                idemRecord.setIdempotencyId(jobRecord.getIdempotencyId());
                idemRecord.setJobId(jobRecord.getJobId());

                final DynamoDBTransactionWriteExpression idempotencyExistsCheck = new DynamoDBTransactionWriteExpression()
                        .withConditionExpression(String.format("attribute_not_exists(%s)", IDEMPOTENCY_ID));
                final TransactionWriteRequest transactionWriteRequest = new TransactionWriteRequest();
                transactionWriteRequest.addPut(idemRecord, idempotencyExistsCheck);
                transactionWriteRequest.addPut(jobRecord);

                transactionWrite(transactionWriteRequest,
                        makeString("Idempotency Id must not already exist for id ", jobRecord.getIdempotencyId()),
                        ComputeErrorCodes.IDEMPOTENT_JOB_EXISTS);
            }
        } catch (final AmazonServiceException e) {
            log.warn("AmazonServiceException thrown when writing job database record for id {}", jobRecord.getJobId());
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        } catch (final AmazonClientException e) {
            log.warn("AmazonClientException thrown when writing job database record for id {}", jobRecord.getJobId());
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_BAD_REQUEST, e);
        }
        return jobRecord;
    }

    public List<JobDBRecord> insertJobs(final List<JobDBRecord> jobRecords) throws ComputeException {
        final long ttl = fillJobTtlSeconds();
        final List<String> statusUpdate = Arrays.asList(getJobStatusTimestampString(JobStatus.SCHEDULED));
        try {
            // Set ttl
            jobRecords.stream().forEach(r -> {
                r.setTtl(ttl);
                r.setStatus(JobStatus.SCHEDULED.toString());
                r.setStatusUpdates(statusUpdate);
            });
            final List<DynamoDBMapper.FailedBatch> failedBatches = getProgressTableDbMapper().batchSave(jobRecords);

            // Retry unprocessed items.
            for (final DynamoDBMapper.FailedBatch failedBatch : failedBatches) {
                // Check for unprocessed keys which could happen if you exceed
                // provisioned throughput
                final Map<String, List<WriteRequest>> unprocessedItems = failedBatch.getUnprocessedItems();
                retryUnprocessedWrites(unprocessedItems);
            }
        } catch (final AmazonServiceException e) {
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        } catch (final AmazonClientException e) {
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_BAD_REQUEST, e);
        }
        return jobRecords;
    }

    @SneakyThrows
    private void retryUnprocessedWrites(Map<String, List<WriteRequest>> unprocessedItems) {
        if (unprocessedItems != null && unprocessedItems.size() > 0) {
            int tryCount = 0;
            final int baseDelayMilliseconds = 500;
            do {
                tryCount++;
                final int delay = (int) (1L << tryCount) * baseDelayMilliseconds + random.nextInt(baseDelayMilliseconds);
                TimeUnit.MILLISECONDS.sleep(delay);
                log.info("Retrying {} unprocessed batch writes", unprocessedItems.size());
                final BatchWriteItemOutcome outcome = getDynamoDB().batchWriteItemUnprocessed(unprocessedItems);
                unprocessedItems = outcome.getUnprocessedItems();
            } while (unprocessedItems != null && unprocessedItems.size() > 0 && tryCount < 3);
        }
    }

    private void transactionWrite(final TransactionWriteRequest writeRequest, final String description, final ComputeErrorCodes computeExceptionCode) throws ComputeException {
        try {
            dynamoDbTransactionMapper.transactionWrite(writeRequest);
        } catch (final ConditionalCheckFailedException e) {
            log.warn(description, e);
            throw new ComputeException(computeExceptionCode, description, e);
        }
    }

    public JobDBRecord getJob(final String jobId) throws ComputeException {
        final JobDBRecord jobRecord;
        try {
            Metrics.getInstance().reportGetJobCall();
            jobRecord = consistentReadDynamoDBMapper.load(JobDBRecord.class, jobId);
        } catch (final Exception e) {
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        }
        return jobRecord;
    }

    private DynamoDBQueryExpression<JobDBRecord> buildSearchArrayJobQueryExpression(final String jobIdPrefix, final String nextToken) throws ComputeException {
        final NameMap expressionNames = new NameMap();
        expressionNames.put("#hashKeyName", ARRAY_JOB_ID);

        final ImmutableMap.Builder<String, AttributeValue> expressionValues = ImmutableMap.<String, AttributeValue>builder()
                .put(":hashKeyValue", new AttributeValue().withS(jobIdPrefix));

        final DynamoDBQueryExpression<JobDBRecord> queryExpression = new DynamoDBQueryExpression<JobDBRecord>()
                .withScanIndexForward(true)
                .withConsistentRead(false)
                .withIndexName(this.dynamoDbArrayJobIndex)
                .withKeyConditionExpression("#hashKeyName = :hashKeyValue")
                .withScanIndexForward(false);

        queryExpression.withExpressionAttributeNames(expressionNames);
        queryExpression.withExpressionAttributeValues(expressionValues.build());

        // return a page-aware expression if last key was given
        if (!StringUtils.isNullOrEmpty(nextToken)) {
            return queryExpression.withExclusiveStartKey(getAttributeMapFromNextTokenArrayJobs(nextToken));
        }

        return queryExpression;
    }

    public SearchJobsResult getArrayJobs(final String jobIdPrefix, final String nextToken) throws ComputeException {
        try {
            log.debug("getArrayJobs: searching with jobIdPrefix={} , nextToken(parsed)= {}", jobIdPrefix, parseNextToken(nextToken));
            final DynamoDBQueryExpression<JobDBRecord> queryExpression = buildSearchArrayJobQueryExpression(jobIdPrefix, nextToken);

            // query specific page
            final QueryResultPage<JobDBRecord> queryResult = getProgressTableDbMapper().queryPage(JobDBRecord.class, queryExpression);
            final List<JobDBRecord> jobDBRecords = queryResult.getResults();
            final String newNextToken = makeNextTokenForArrayJobs(queryResult.getLastEvaluatedKey());
            if (log.isDebugEnabled())
                log.debug(makeString("getArrayJobs: returning ", jobDBRecords.size(), " jobs with new nextToken(parsed)=", parseNextToken(newNextToken)));
            return new SearchJobsResult(jobDBRecords, newNextToken);
        } catch (final AmazonClientException e) {
            log.error(makeString("getArrayJobs: caught exception with jobIdPrefix=", jobIdPrefix, ", nextToken=", nextToken), e);
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        }
    }

    private Map<String, AttributeValue> getAttributeMapFromNextTokenArrayJobs(final String nextToken) throws ComputeException {
        // translate NextToken to an attribute map, something our client method can swallow
        if (nextToken == null) {
            return null;
        }
        final List<String> fields = parseNextToken(nextToken);
        if (fields.size() != 3) {
            throw new ComputeException(ComputeErrorCodes.BAD_REQUEST, makeString("nextToken should have three fields, this token has ", fields.size()));
        }
        return ImmutableMap.<String, AttributeValue>builder()
                .put(ARRAY_JOB_ID, new AttributeValue().withS(fields.get(0)))
                .put(ARRAY_INDEX, new AttributeValue().withN(fields.get(1)))
                .put(JOB_ID, new AttributeValue().withS(fields.get(2))).build();
    }

    private String makeNextTokenForArrayJobs(final Map<String, AttributeValue> attributeValueMap) {
        // translate NextToken to an attribute map, something our client method can swallow
        if (attributeValueMap == null) {
            return null;
        }
        final String arrayJobId = Optional.ofNullable(attributeValueMap.get(ARRAY_JOB_ID)).map(AttributeValue::getS).orElse("");
        final String arrayJobIndex = Optional.ofNullable(attributeValueMap.get(ARRAY_INDEX)).map(AttributeValue::getN).orElse("");
        final String jobId = Optional.ofNullable(attributeValueMap.get(JOB_ID)).map(AttributeValue::getS).orElse("");
        if (arrayJobId.isEmpty() || arrayJobIndex.isEmpty() || jobId.isEmpty()) {
            log.warn("makeNextTokenForArrayJobs: one or more nextToken attributes is blank: {}", attributeValueMap);
            return null;
        }
        return makeNextToken(arrayJobId, arrayJobIndex, jobId);
    }

    private DynamoDBQueryExpression<JobDBRecord> buildRangedSearchQueryExpression(final String serviceClientId, final String nextToken, final Integer maxResults, final long utcTimeFrom, final long utcTimeTo, final Optional<String> tag) throws ComputeException {
        // build the name map
        final NameMap expressionNames = new NameMap();
        expressionNames.put("#hashKeyName", SERVICE_CLIENT_ID);
        expressionNames.put("#rangeKeyName", MODIFICATION_TIME);

        // build the value map
        final ImmutableMap.Builder<String, AttributeValue> expressionValues = ImmutableMap.<String, AttributeValue>builder()
                .put(":hashKeyValue", new AttributeValue().withS(serviceClientId))
                .put(":rangeFrom", new AttributeValue().withN(Long.toString(utcTimeFrom)))
                .put(":rangeTo", new AttributeValue().withN(Long.toString(utcTimeTo)));

        // build query expression
        final DynamoDBQueryExpression<JobDBRecord> queryExpression = new DynamoDBQueryExpression<JobDBRecord>()
                .withScanIndexForward(true)
                .withLimit(maxResults)
                .withConsistentRead(false)
                .withIndexName(this.dynamoDbSearchIndex)
                .withKeyConditionExpression("#hashKeyName = :hashKeyValue AND #rangeKeyName BETWEEN :rangeFrom AND :rangeTo")
                .withScanIndexForward(false);

        if (tag.isPresent()) {
            expressionNames.put("#tagsKey", TAGS);
            expressionValues.put(":tag", new AttributeValue().withS(tag.get()));
            queryExpression.withFilterExpression("contains (#tagsKey, :tag)");
        }
        queryExpression.withExpressionAttributeNames(expressionNames);
        queryExpression.withExpressionAttributeValues(expressionValues.build());

        // return a page-aware expression if last key was given
        if (!StringUtils.isNullOrEmpty(nextToken)) {
            return queryExpression.withExclusiveStartKey(getAttributeMapFromNextTokenSearchByTime(nextToken));
        }

        return queryExpression;
    }

    private Map<String, AttributeValue> getAttributeMapFromNextTokenSearchByTime(final String nextToken) throws ComputeException {
        Map<String, AttributeValue> attrMap = null;
        // translate NextToken to an attribute map, something our client method can swallow
        if (nextToken == null) {
            return attrMap;
        }

        final List<String> fields = parseNextToken(nextToken);
        if (fields.size() != 3) {
            throw new ComputeException(ComputeErrorCodes.BAD_REQUEST, makeString("nextToken should have three fields, this token has ", fields.size()));
        }
        attrMap = new HashMap<>();  //NOPMD
        attrMap.put(SERVICE_CLIENT_ID, new AttributeValue().withS(fields.get(0)));
        attrMap.put(MODIFICATION_TIME, new AttributeValue().withN(fields.get(1)));
        attrMap.put(JOB_ID, new AttributeValue().withS(fields.get(2)));
        return attrMap;
    }

    private String makeNextTokenForSearchByTime(final Map<String, AttributeValue> attributeValueMap) {
        // translate NextToken to an attribute map, something our client method can swallow
        if (attributeValueMap == null) {
            return null;
        }

        final String clientID = Optional.ofNullable(attributeValueMap.get(SERVICE_CLIENT_ID)).map(AttributeValue::getS).orElse("");
        final String modTime = Optional.ofNullable(attributeValueMap.get(MODIFICATION_TIME)).map(AttributeValue::getN).orElse("");
        final String jobId = Optional.ofNullable(attributeValueMap.get(JOB_ID)).map(AttributeValue::getS).orElse("");
        if (clientID.isEmpty() || modTime.isEmpty() || jobId.isEmpty()) {
            log.warn("makeNextTokenForSearchByTime: one or more nextToken attributes is blank: {}", attributeValueMap);
            return null;
        }
        return makeNextToken(clientID, modTime, jobId);
    }

    public Map<String, Object> getJobsByTimeForPage(
            final String serviceClientId, final String nextToken, final Integer maxResults, final long utcTimeFrom, final long utcTimeTo, final Optional<String> tag) {
        final Map<String, Object> results = new HashMap<>();  //NOPMD
        try {
            log.debug("getJobsByTimeForPage: searching with nextToken(parsed)={}", parseNextToken(nextToken));
            final DynamoDBQueryExpression<JobDBRecord> queryExpression =
                    buildRangedSearchQueryExpression(serviceClientId, nextToken, maxResults, utcTimeFrom, utcTimeTo, tag);

            // query specific page
            final QueryResultPage<JobDBRecord> queryResult = getProgressTableDbMapper().queryPage(JobDBRecord.class, queryExpression);
            final List<JobDBRecord> jobDBRecords = queryResult.getResults();
            final String newNextToken = makeNextTokenForSearchByTime(queryResult.getLastEvaluatedKey());
            results.put(NEXT_TOKEN_FIELD, newNextToken);
            results.put(JOBS_FIELD, jobDBRecords);
            if (log.isDebugEnabled())
                log.debug(makeString("getJobsByTimeForPage: returning ", jobDBRecords.size(), " jobs with new nextToken(parsed)=", parseNextToken(newNextToken)));
        } catch (final Exception e) {
            log.error(makeString("getJobsByTimeForPage: caught exception with nextToken=", nextToken,
                    ", maxResults=", maxResults,
                    ", utcTimeFrom=", utcTimeFrom,
                    ", utcTimeTo=", utcTimeTo), e);
            results.put(ERROR_FIELD, e);
        }
        return results;
    }

    public PaginatedQueryList<JobDBRecord> getJobsByTime(
            final String serviceClientId, final Integer maxResults, final long utcTimeFrom, final long utcTimeTo, final Optional<String> tag) {
        try {
            final DynamoDBQueryExpression<JobDBRecord> queryExpression =
                    buildRangedSearchQueryExpression(serviceClientId, null, maxResults, utcTimeFrom, utcTimeTo, tag);

            // query dbMapper, returns auto-paginated response via PaginatedQueryList<JobDBRecord>
            return getProgressTableDbMapper().query(JobDBRecord.class, queryExpression);

        } catch (final Exception e) {
            log.error(makeString("getJobsByTime caught exception with maxResults=", maxResults,
                    ", utcTimeFrom=", utcTimeFrom,
                    ", utcTimeTo=", utcTimeTo), e);
            return null;
        }
    }

    public UpdateItemOutcome updateItemSpec(final String tableName, final UpdateItemSpec updateItemSpec, final String itemDescription, final String conditionDescription) throws ComputeException {
        try {
            final Table table = getDynamoDB().getTable(tableName);
            return updateWithConditionCheckDescription(table, updateItemSpec, conditionDescription);
        } catch (final ConditionalCheckFailedException e) {
            final String error = makeString(CAN_T_PERFORM_UPDATE_ON_RECORED, itemDescription, ", Table: ", tableName);
            throw new ComputeException(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN, error, e);
        } catch (final AmazonClientException e) {
            log.warn(makeString(FAILED_TO_UPDATE_RECORD, itemDescription, ", Table: ", tableName), e);
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        } catch (final ComputeException e) {
            log.error(makeString(UNEXPECTED_ERROR_WHILE_UPDATING_RECORD, itemDescription, ", Table: ", tableName), e);
            throw e;
        } catch (final Exception e) {
            log.error(makeString(UNEXPECTED_ERROR_WHILE_UPDATING_RECORD, itemDescription, ", Table: ", tableName), e);
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.SERVER_UNEXPECTED, e);
        }
    }

    public void updateHeartbeat(final String jobId) throws ComputeException {
        final long currentTimeMillis = new Date().getTime();
        // Update the LastHeartbeatTime field only if the
        final String updateExpression = "set #lhbt = :lhbt, #mt = :mt";
        final NameMap nameMap = new NameMap()
                .with("#lhbt", LAST_HEARTBEAT_TIME)
                .with("#mt", MODIFICATION_TIME);
        final ValueMap valueMap = new ValueMap()
                .with(":lhbt", currentTimeMillis)
                .with(":mt", currentTimeMillis)
                .with(":hbs", HeartbeatStatus.ENABLED.toString());
        final String conditionExpression = String.format(
                "attribute_exists(%1$s) and ( (attribute_exists(%2$s) and %2$s = :hbs) or attribute_not_exists(%2$s) )",
                JOB_ID, HEARTBEAT_STATUS);
        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, jobId)
                .withConditionExpression(conditionExpression)
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(valueMap);
        updateItemSpec(dynamoDbJobsTable, updateItemSpec, makeString(JOB_ID, ": ", jobId), "JobId exists and heartbeating is allowed");
    }

    public void updateProgress(final String jobId, final Integer percentComplete, final String details) throws ComputeException {
        final String updateExpression = "set #i = :i, #j = :j, #m = :m REMOVE #dj";

        final NameMap nameMap = new NameMap()
                .with("#i", PERCENT_COMPLETE)
                .with("#j", BINARY_DETAILS)
                .with("#dj", DETAILS)
                .with("#m", MODIFICATION_TIME);
        final ValueMap valueMap = new ValueMap()
                .with(":i", percentComplete)
                .with(":j", compressString(details))
                .with(":m", new Date().getTime())
                .with(":hbs", HeartbeatStatus.ENABLED.toString());

        final String conditionExpression = String.format(
                "attribute_exists(%1$s) and ( (attribute_exists(%2$s) and %2$s = :hbs) or attribute_not_exists(%2$s) )",
                JOB_ID, HEARTBEAT_STATUS);
        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, jobId)
                .withConditionExpression(conditionExpression)
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(valueMap);
        updateItemSpec(dynamoDbJobsTable, updateItemSpec, makeString(JOB_ID, ": ", jobId), "JobId exists and heartbeating is allowed");
    }

    public void updateToken(@NonNull final String jobId, @NonNull final String token) throws ComputeException {
        final String updateExpression = "set #t = :t, #m = :m";
        final NameMap nameMap = new NameMap()
                .with("#t", OXYGEN_TOKEN)
                .with("#m", MODIFICATION_TIME);
        final ValueMap valueMap = new ValueMap()
                .with(":t", token)
                .with(":m", new Date().getTime());

        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, jobId)
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(valueMap);
        updateItemSpec(dynamoDbJobsTable, updateItemSpec, makeString(JOB_ID, ": ", jobId), "Unconditional");
    }

    public void updateTags(@NonNull final String jobId, @NonNull final List<String> tags) throws ComputeException {
        final long currentTimeMillis = new Date().getTime();
        final String updateExpression = "set #t = :t, #m = :m, #tm = :tm";
        final NameMap nameMap = new NameMap()
                .with("#t", TAGS)
                .with("#m", MODIFICATION_TIME)
                .with("#tm", TAGS_MODIFICATION_TIME);
        final ValueMap valueMap = new ValueMap()
                .with(":t", tags)
                .with(":m", currentTimeMillis)
                .with(":tm", currentTimeMillis);

        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, jobId)
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(valueMap);
        updateItemSpec(dynamoDbJobsTable, updateItemSpec, makeString(JOB_ID, ": ", jobId), "JobId exists");
    }

    private String readStringAttributeFromJobRecord(@NonNull final String jobId, @NonNull final String fieldName) throws ComputeException {
        return readAttributeFromJobRecord(jobId, fieldName, String.class);
    }

    private List<String> readStringListAttributeFromJobRecord(@NonNull final String jobId, @NonNull final String fieldName) throws ComputeException {
        return readAttributeFromJobRecord(jobId, fieldName, (Class<List<String>>) (Class<?>) List.class);
    }

    //    Added a namemap for attribute name which could be reserved keywords. Example - batch
    private <T> T readAttributeFromJobRecord(@NonNull final String jobId, @NonNull final String fieldName, final Class<T> clazz) throws ComputeException {
        try {
            final Map<String, String> nameMap = new NameMap();
            nameMap.put("#fn", fieldName);
            final Table table = getDynamoDB().getTable(dynamoDbJobsTable);
            final Item jobItem = table.getItem(new PrimaryKey(new KeyAttribute(JOB_ID, jobId)), "#fn", nameMap);
            if (jobItem == null) {
                final Item item = table.getItem(new PrimaryKey(new KeyAttribute(JOB_ID, jobId)), JOB_ID, null);
                if (item == null) {
                    throw new ComputeException(ComputeErrorCodes.NOT_FOUND, makeString("Compute Job ", jobId, " does not exist, or has expired."));
                } else {
                    throw new ComputeException((ComputeErrorCodes.NOT_FOUND), makeString("Compute Job", jobId, "does not have the field that was searched ", fieldName));
                }
            }
            return clazz.cast(jobItem.get(fieldName));
        } catch (final SdkBaseException e) {
            throw new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        }
    }

    public String getToken(final String jobId) throws ComputeException {
        return readStringAttributeFromJobRecord(jobId, OXYGEN_TOKEN);
    }

    public List<String> getTags(final String jobId) throws ComputeException {
        return readStringListAttributeFromJobRecord(jobId, TAGS);
    }

    public List<String> getStepExecutionArns(final String jobId) throws ComputeException {
        return readStringListAttributeFromJobRecord(jobId, STEP_EXECUTION_ARNS);
    }

    public boolean isBatchJob(final String jobId) throws ComputeException {
        final BigDecimal isBatch = readAttributeFromJobRecord(jobId, IS_BATCH_JOB, BigDecimal.class);
        return isBatch.intValueExact() != 0;
    }

    public void deleteJob(final String jobId) throws ComputeException {
        try {
            final Table table = getDynamoDB().getTable(dynamoDbJobsTable);
            table.deleteItem(JOB_ID, jobId);
        } catch (final AmazonClientException e) {
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        }
    }

    // This should only be called from the tests. Let the heartbeat lambda track it otherwise.
    public void setHeartbeatStatus(final String jobId, final HeartbeatStatus status) throws ComputeException {
        final String updateExpression = "set #hs = :hs";
        final NameMap nameMap = new NameMap()
                .with("#hs", HEARTBEAT_STATUS);
        final ValueMap valueMap = new ValueMap()
                .withString(":hs", status.toString());

        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, jobId)
                .withConditionExpression(getJobIdExistsCondition())
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(valueMap);
        updateItemSpec(dynamoDbJobsTable, updateItemSpec, makeString(JOB_ID, ": ", jobId), JOB_ID_MUST_EXIST);
    }

    @SuppressWarnings("squid:S1192")
    private void updateJobStatus(final UpdateJobStatusRequest request) throws ComputeException {

        final List<TransactWriteItem> actions = new ArrayList<>();

        if (!StringUtils.isNullOrEmpty(request.getIdempotencyId()) &&
                (request.getStatus() == JobStatus.FAILED || request.getStatus() == JobStatus.CANCELED)) {
            final Delete deleteIdempotentJob = deleteIdempotentJobAction(request.getIdempotencyId());
            actions.add(new TransactWriteItem().withDelete(deleteIdempotentJob));
        }

        // Since there is no conditional check on increment it's safe to run increment in transaction
        // For decrement, failure of conditional check count > 0 can be ignored and should be run separately for robustness
        Boolean isIncrementActiveCount = null;
        if (!StringUtils.isNullOrEmpty(request.getUserServiceWorker()) &&
                (request.getStatus().isActiveState() || request.getStatus().isTerminalState())) {
            isIncrementActiveCount = request.getStatus().isActiveState();
            if (request.getStatus().isActiveState()) {
                final Update updateActiveJobCount = updateActiveJobsCountAction(request.getUserServiceWorker(), true);
                actions.add(new TransactWriteItem().withUpdate(updateActiveJobCount));
            }
        }

        // Run transaction if there were other actions needed for this updated else run the single update without transaction
        if (!actions.isEmpty()) {
            final Update jobUpdate = updateJobStatusAction(request);
            actions.add(new TransactWriteItem().withUpdate(jobUpdate));
            final TransactWriteItemsRequest updateJobStatus = new TransactWriteItemsRequest().withTransactItems(actions);
            transactWriteItems(request.getJobId(), updateJobStatus);
        } else {
            // Since it's a single update no transaction needed
            updateJobStatusDirect(request);
        }

        // For decrement on active jobs count we need to run this update separately from previous transaction.
        if (isIncrementActiveCount != null && !isIncrementActiveCount) {
            try {
                updateActiveJobsCountDirect(request.getUserServiceWorker(), false);
            } catch (final ComputeException e) {
                log.warn(makeString("Failed to decrement Active jobs count for: ", request.getJobId(), ", it's likely at 0"), e);
            }
        }
    }

    public void markJobScheduled(final String jobId, final String userServiceWorker) throws ComputeException {
        final UpdateJobStatusRequest request = new UpdateJobStatusRequest()
                .withJobId(jobId)
                .withStatus(JobStatus.SCHEDULED)
                .withUserServiceWorker(userServiceWorker);
        updateJobStatus(request);
    }

    public void markJobInprogress(final String jobId) throws ComputeException {
        final UpdateJobStatusRequest request = new UpdateJobStatusRequest()
                .withJobId(jobId)
                .withStatus(JobStatus.INPROGRESS);
        updateJobStatus(request);
    }

    public void markJobCanceled(final JobDBRecord jobRecord) throws ComputeException {
        markJobCanceled(jobRecord.getJobId(), jobRecord.getIdempotencyId(), jobRecord.getUserServiceWorkerId());
    }

    public void markJobCanceled(final String jobId, final String idempotencyId, final String userServiceWorker) throws ComputeException {
        final UpdateJobStatusRequest request = new UpdateJobStatusRequest()
                .withJobId(jobId)
                .withIdempotencyId(idempotencyId)
                .withStatus(JobStatus.CANCELED)
                .withUserServiceWorker(userServiceWorker);
        updateJobStatus(request);
    }

    public void markJobFailed(final String jobId, final String idempotencyId, final List<Failure> failures, final String output, final String userServiceWorker) throws ComputeException {
        final UpdateJobStatusRequest request = new UpdateJobStatusRequest()
                .withJobId(jobId)
                .withIdempotencyId(idempotencyId)
                .withStatus(JobStatus.FAILED)
                .withFailures(failures)
                .withOutput(output)
                .withUserServiceWorker(userServiceWorker);
        updateJobStatus(request);
    }

    public void markJobCompleted(final String jobId, final List<Failure> failures, final String output, final String userServiceWorker) throws ComputeException {
        final UpdateJobStatusRequest request = new UpdateJobStatusRequest()
                .withJobId(jobId)
                .withStatus(JobStatus.COMPLETED)
                .withFailures(failures)
                .withOutput(output)
                .withUserServiceWorker(userServiceWorker);
        updateJobStatus(request);
    }

    public BigDecimal getJobCount() throws ComputeException {
        try {
            final Table table = getDynamoDB().getTable(dynamoDbJobsTable);
            return new BigDecimal(table.describe().getItemCount());
        } catch (final AmazonClientException e) {
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        }
    }

    public void setBatchJobId(final String jobId, final String spawnedBatchJobId) throws ComputeException {
        final String updateExpression = "set #sbji = :sbji";
        final NameMap nameMap = new NameMap()
                .with("#sbji", SPAWNED_BATCH_JOB_ID);
        final ValueMap valueMap = new ValueMap()
                .with(":sbji", spawnedBatchJobId);

        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, jobId)
                .withConditionExpression(getJobIdExistsCondition())
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(valueMap);
        updateItemSpec(dynamoDbJobsTable, updateItemSpec, makeString(JOB_ID, ": ", jobId), JOB_ID_MUST_EXIST);
    }

    public void addStepExecutionArn(final String jobId, final String stepExecutionArn) throws ComputeException {
        final NameMap nameMap = new NameMap()
                .with(SEARN_VALUE, STEP_EXECUTION_ARNS);
        final ValueMap valueMap = new ValueMap()
                .withList(SEARN_FIELD, Arrays.asList(stepExecutionArn))
                .withList(EMPTY_LIST, Collections.emptyList());

        final String updateExpression = "set #searn = list_append(if_not_exists(#searn, :empty_list), :searn)";

        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, jobId)
                .withConditionExpression(getJobIdExistsCondition())
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(valueMap);
        updateItemSpec(dynamoDbJobsTable, updateItemSpec, makeString(JOB_ID, ": ", jobId), JOB_ID_MUST_EXIST);
    }

    @SuppressWarnings("squid:S1192")
    public JobDBRecord enqueueJob(@NonNull final String jobId, @NonNull final String queueId) throws ComputeException {

        final JobDBRecord record = this.getJob(jobId);

        if (record.getQueueInfo() == null) {
            record.initQueueInfo(queueId);
            this.getProgressTableDbMapper().save(record);
            return record;
        } else {
            final long tsUTC = System.currentTimeMillis();
            if (record.getQueueInfo().isInQueue()) {
                return record;
            }
            final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                    .withPrimaryKey(new PrimaryKey()).withPrimaryKey(jobId, jobId)
                    .withUpdateExpression("ADD #qinfo.#v :one "
                            + "SET #qid = :qid, #qinfo.#queued = :true, #qinfo.#selected = :false, "
                            + "#qinfo.#uts = :ts, #qinfo.#ats = :ts, #qinfo.#st = :st, "
                            + "#st = :st, #jsu = list_append(if_not_exists(#jsu, :empty_list), :jsu)")
                    .withNameMap(new NameMap()
                            .with("#v", VERSION)
                            .with("#st", STATUS)
                            .with("#qid", QUEUE_ID)
                            .with("#qinfo", QUEUE_INFO)
                            .with("#queued", QUEUED)
                            .with("#selected", SELECTED_FROM_QUEUE)
                            .with("#uts", LAST_UPDATED_TIMESTAMP)
                            .with("#ats", ADD_TO_QUEUE_TIMESTAMP))
                    .withValueMap(new ValueMap()
                            .withBoolean(":true", true)
                            .withBoolean(":false", false)
                            .withString(":qid", queueId)
                            .withInt(":v", record.getQueueInfo().getVersion())
                            .withString(":st", JobStatus.QUEUED.toString())
                            .withList(EMPTY_LIST, new ArrayList<>())
                            .withString(":jsu", getJobStatusTimestampString(JobStatus.QUEUED))
                            .withLong(":ts", tsUTC))
                    .withConditionExpression(CONDITIONAL_CHECK_QINFO_V_V)
                    .withReturnValues(ReturnValue.ALL_NEW);

            final UpdateItemOutcome updateItemOutcome = updateItemSpec(dynamoDbJobsTable,
                    updateItemSpec, makeString(JOB_ID, ": ", jobId), "Failed to update job during enqueueJob.");
            return this.getProgressTableDbMapper().marshallIntoObject(JobDBRecord.class, updateItemOutcome.getUpdateItemResult().getAttributes());
        }
    }

    // For mocking, and faster unit tests
    public int getQueueVisibilityTimeout() {
        return QueueInfo.VISIBILITY_TIMEOUT_IN_SECONDS;
    }

    private boolean queuedItemIsStale(final Map<String, AttributeValue> infoMap) {
        final long lastPeekTimeUTC = Optional.ofNullable(infoMap.getOrDefault(PEEK_FROM_QUEUE_TIMESTAMP, null))
                .map(AttributeValue::getN).map(Long::parseLong).orElse(0L);
        return System.currentTimeMillis() - lastPeekTimeUTC > getQueueVisibilityTimeout() * 1_000;
    }

    @SuppressWarnings("squid:S1192")
    public JobDBRecord peekJob(@NonNull final String queueId) {
        final Map<String, AttributeValue> values = new HashMap<>();    //NOPMD
        values.put(":qid", new AttributeValue().withS(queueId));
        Map<String, AttributeValue> exclusiveStartKey = null;

        do {
            final QueryRequest queryRequest = getPeekQueryRequest(exclusiveStartKey, values);
            final QueryResult queryResult = this.getAmazonDynamoDB().query(queryRequest);
            exclusiveStartKey = queryResult.getLastEvaluatedKey();

            for (final Map<String, AttributeValue> itemMap : queryResult.getItems()) {
                final Map<String, AttributeValue> infoMap = itemMap.get(QUEUE_INFO).getM();
                final boolean isQueueSelected = Optional.ofNullable(infoMap.getOrDefault(SELECTED_FROM_QUEUE, null)).map(AttributeValue::getBOOL).orElse(false);

                // Found one to return. Try to mark it queued
                if (!isQueueSelected || queuedItemIsStale(infoMap)) {
                    final String selectedJobID = itemMap.get(JOB_ID).getS();
                    final int selectedVersion = Integer.parseInt(infoMap.get(VERSION).getN());
                    try {
                        return markJobPeeked(selectedJobID, selectedVersion);
                    } catch (final ComputeException e) {
                        log.info("Race condition?: exception marking job as peeked. Continuing to the next one. {}",
                                selectedJobID);
                        // continue; -- Continuing on to the next item; this is documentary.
                    }
                }
            }
        } while (exclusiveStartKey != null);
        return null;
    }

    private QueryRequest getPeekQueryRequest(final Map<String, AttributeValue> exclusiveStartKey, final Map<String, AttributeValue> values) {
        return new QueryRequest()
                .withProjectionExpression(makeString(JOB_ID, ",", QUEUE_ID, ",", QUEUE_INFO))
                .withIndexName(this.dynamoDbQueueingIndex)
                .withTableName(this.dynamoDbJobsTable)
                .withKeyConditionExpression(makeString(QUEUE_ID, "=", ":qid"))
                .withLimit(10)
                .withScanIndexForward(true)
                .withExpressionAttributeValues(values)
                .withExclusiveStartKey(exclusiveStartKey);
    }

    @SuppressWarnings("squid:S1192")
    private JobDBRecord markJobPeeked(@NonNull final String jobId, final int selectedVersion) throws ComputeException {

        final long tsUTC = System.currentTimeMillis();

        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, jobId)
                .withUpdateExpression("ADD #qinfo.#v :one "
                        + "SET #qinfo.#selected = :true, #qinfo.#uts = :ts, #qinfo.#pts = :ts, #qinfo.#st = :st")
                .withNameMap(new NameMap()
                        .with("#v", VERSION)
                        .with("#st", STATUS)
                        .with("#qinfo", QUEUE_INFO)
                        .with("#selected", SELECTED_FROM_QUEUE)
                        .with("#uts", LAST_UPDATED_TIMESTAMP)
                        .with("#pts", PEEK_FROM_QUEUE_TIMESTAMP))
                .withValueMap(new ValueMap()
                        .withInt(":one", 1)
                        .withInt(":v", selectedVersion)
                        .withBoolean(":true", true)
                        .withLong(":ts", tsUTC)
                        .withString(":st", QueueInfo.StatusEnum.PROCESSING.toString()))
                .withConditionExpression(CONDITIONAL_CHECK_QINFO_V_V)
                .withReturnValues(ReturnValue.ALL_NEW);
        final UpdateItemOutcome updateItemOutcome = updateItemSpec(dynamoDbJobsTable, updateItemSpec, makeString(JOB_ID, ": ", jobId), "Failed to update job during markJobPeeked.");
        return this.getProgressTableDbMapper().marshallIntoObject(JobDBRecord.class, updateItemOutcome.getUpdateItemResult().getAttributes());
    }

    public JobDBRecord dequeueJob(@NonNull final String queueId) throws ComputeException {

        final JobDBRecord peekResult = this.peekJob(queueId);
        if (peekResult != null) {
            return removeJobFromQueue(peekResult.getJobId(), peekResult.getQueueInfo().getVersion());
        }
        return null;
    }

    public JobDBRecord removeJobFromQueue(final JobDBRecord jobRecord) throws ComputeException {
        return removeJobFromQueue(jobRecord.getJobId(), jobRecord.getQueueInfo().getVersion());
    }

    @SuppressWarnings("squid:S1192")
    public JobDBRecord removeJobFromQueue(@NonNull final String jobId, final int version) throws ComputeException {
        final long tsUTC = System.currentTimeMillis();
        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, jobId)
                .withUpdateExpression("ADD #qinfo.#v :one "
                        + "REMOVE #qinfo.#pts, #qid  "
                        + "SET #qinfo.#queued = :false, #qinfo.#st = :st, #qinfo.#selected = :false, " +
                        "#qinfo.#uts = :ts, #qinfo.#rts = :ts")
                .withNameMap(new NameMap()
                        .with("#qid", QUEUE_ID)
                        .with("#qinfo", QUEUE_INFO)
                        .with("#v", VERSION)
                        .with("#st", STATUS)
                        .with("#queued", QUEUED)
                        .with("#selected", SELECTED_FROM_QUEUE)
                        .with("#pts", PEEK_FROM_QUEUE_TIMESTAMP)
                        .with("#uts", LAST_UPDATED_TIMESTAMP)
                        .with("#rts", REMOVE_FROM_QUEUE_TIMESTAMP))
                .withValueMap(new ValueMap()
                        .withInt(":one", 1)
                        .withBoolean(":false", false)
                        .withInt(":v", version)
                        .withString(":st", QueueInfo.StatusEnum.REMOVED.toString())
                        .withLong(":ts", tsUTC))
                .withConditionExpression(CONDITIONAL_CHECK_QINFO_V_V)
                .withReturnValues(ReturnValue.ALL_NEW);

        final UpdateItemOutcome updateItemOutcome = updateItemSpec(dynamoDbJobsTable, updateItemSpec,
                makeString(JOB_ID, ": ", jobId), "Failed to update job during removeJobFromQueue.");
        return this.getProgressTableDbMapper().marshallIntoObject(JobDBRecord.class, updateItemOutcome.getUpdateItemResult().getAttributes());
    }

    private void transactWriteItems(@NonNull final String jobId, final TransactWriteItemsRequest writeItemsRequest) throws ComputeException {
        try {
            getAmazonDynamoDB().transactWriteItems(writeItemsRequest);
        } catch (final ConditionalCheckFailedException e) {
            final String error = makeString(CAN_T_PERFORM_UPDATE_ON_RECORED, JOB_ID, ": ", jobId);
            log.warn(error, e);
            throw new ComputeException(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN, error, e);
        } catch (final TransactionConflictException e) {
            final String error = makeString(CAN_T_PERFORM_UPDATE_ON_RECORED, JOB_ID, ": ", jobId);
            log.warn(error, e);
            throw new ComputeException(ComputeErrorCodes.DYNAMODB_TRANSACTION_CONFLICT, error, e);
        } catch (final TransactionCanceledException e) {
            for (final CancellationReason reason : e.getCancellationReasons()) {
                if ("ConditionalCheckFailed".equals(reason.getCode())) {
                    final String error = makeString(CAN_T_PERFORM_UPDATE_ON_RECORED, JOB_ID, ": ", jobId);
                    log.warn(error, e);
                    throw new ComputeException(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN, error, e);
                } else if ("TransactionConflict".equals(reason.getCode())) {
                    final String error = makeString(CAN_T_PERFORM_UPDATE_ON_RECORED, JOB_ID, ": ", jobId);
                    log.warn(error, e);
                    throw new ComputeException(ComputeErrorCodes.DYNAMODB_TRANSACTION_CONFLICT, error, e);
                }
            }
            log.warn(makeString(FAILED_TO_UPDATE_RECORD, JOB_ID, ": ", jobId), e);
            throw new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        } catch (final AmazonClientException e) {
            log.warn(makeString(FAILED_TO_UPDATE_RECORD, JOB_ID, ": ", jobId), e);
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        } catch (final Exception e) {
            log.error(makeString(UNEXPECTED_ERROR_WHILE_UPDATING_RECORD, JOB_ID, ": ", jobId), e);
            throw new ComputeException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.SERVER_UNEXPECTED, e);
        }
    }

    // Action for transaction
    private void updateJobStatusDirect(final UpdateJobStatusRequest request) throws ComputeException {

        String updateExpression = "set #st = :st, #hbs = :hbs, #mt = :mt," + " #jsu = list_append(if_not_exists(#jsu, :empty_list), :jsu)";

        final HeartbeatStatus hbStatus = request.getStatus().isActiveState() ? HeartbeatStatus.ENABLED : HeartbeatStatus.DISABLED;

        final NameMap nameMap = new NameMap()
                .with("#st", STATUS)
                .with("#hbs", HEARTBEAT_STATUS)
                .with("#mt", MODIFICATION_TIME)
                .with("#jsu", STATUS_UPDATES);

        final ValueMap valueMap = new ValueMap()
                .withString(":st", request.getStatus().toString())
                .withString(":hbs", hbStatus.toString())
                .withNumber(":mt", fillCurrentTimeMilliseconds())
                .withList(":jsu", Arrays.asList(getJobStatusTimestampString(request.getStatus())))
                .withList(EMPTY_LIST, Collections.emptyList())
                .withString(":stpro", JobStatus.INPROGRESS.toString())
                .withString(":stcom", JobStatus.COMPLETED.toString())
                .withString(":stcan", JobStatus.CANCELED.toString());

        if (request.getFailures() != null && !request.getFailures().isEmpty()) {
            nameMap.put("#err", ERRORS);
            final List<String> errors = request.getFailures().stream().map(Json::toJsonString).collect(Collectors.toList());
            valueMap.withList(":err", errors);
            updateExpression += ", #err = list_append(#err, :err)";
        }

        if (!ComputeStringOps.isNullOrEmpty(request.getOutput())) {
            nameMap.put("#o", BINARY_RESULT);
            final ByteBuffer byteBuffer = compressString(request.getOutput());
            final byte[] buffer = new byte[byteBuffer.remaining()];
            byteBuffer.get(buffer);
            valueMap.withBinary(":o", buffer);
            updateExpression += ", #o = :o";
        }


        if (request.getStatus().isTerminalState()) {
            // This should have been milliseconds
            final long jobCompletionTime = fillCurrentTimeSeconds();
            nameMap.put("#jft", JOB_FINISHED_TIME);
            nameMap.put("#ttl", TTL);
            valueMap.withLong(":jft", jobCompletionTime);
            valueMap.withLong(":ttl", fillJobTtlSeconds());
            updateExpression += ", #jft = :jft, #ttl = :ttl";
        }


        // Leave REMOVE at the end; it is not part of the set "x=y" expression
        if (!ComputeStringOps.isNullOrEmpty(request.getOutput())) {
            nameMap.put("#dr", RESULT);
            updateExpression += " REMOVE #dr";
        }

        final String conditionExpression = makeString(getJobIdExistsCondition(),
                " AND (:st = :stpro OR NOT #st in (:stcom, :stcan, :st))");

        log.info(makeString("Updating job id ", request.getJobId(), " to status ", request.getStatus().toString()));

        final UpdateItemSpec updateItemSpec = new UpdateItemSpec()
                .withPrimaryKey(JOB_ID, request.getJobId())
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(valueMap)
                .withConditionExpression(conditionExpression);

        updateItemSpec(dynamoDbJobsTable, updateItemSpec, makeString(JOB_ID, ": ", request.getJobId()), "Failed to update job status.");
    }

    // Action for transaction
    private Update updateJobStatusAction(final UpdateJobStatusRequest request) {

        String updateExpression = "set #st = :st, #hbs = :hbs, #mt = :mt," + " #jsu = list_append(if_not_exists(#jsu, :empty_list), :jsu)";

        final HeartbeatStatus hbStatus = request.getStatus().isActiveState() ? HeartbeatStatus.ENABLED : HeartbeatStatus.DISABLED;

        final NameMap nameMap = new NameMap()
                .with("#st", STATUS)
                .with("#hbs", HEARTBEAT_STATUS)
                .with("#mt", MODIFICATION_TIME)
                .with("#jsu", STATUS_UPDATES);

        final ImmutableMap.Builder<String, AttributeValue> valueMap = ImmutableMap.<String, AttributeValue>builder()
                .put(":st", new AttributeValue(request.getStatus().toString()))
                .put(":hbs", new AttributeValue(hbStatus.toString()))
                .put(":mt", new AttributeValue().withN(Long.toString(fillCurrentTimeMilliseconds())))
                .put(":jsu", new AttributeValue().withL(new AttributeValue(getJobStatusTimestampString(request.getStatus()))))
                .put(EMPTY_LIST, new AttributeValue().withL(Collections.emptyList()))
                .put(":stpro", new AttributeValue(JobStatus.INPROGRESS.toString()))
                .put(":stcom", new AttributeValue(JobStatus.COMPLETED.toString()))
                .put(":stcan", new AttributeValue(JobStatus.CANCELED.toString()));

        if (request.getFailures() != null && !request.getFailures().isEmpty()) {
            nameMap.put("#err", ERRORS);
            final List<AttributeValue> errors = request.getFailures().stream().map(Json::toJsonString)
                    .map(AttributeValue::new).collect(Collectors.toList());
            valueMap.put(":err", new AttributeValue().withL(errors));
            updateExpression += ", #err = list_append(#err, :err)";
        }

        if (!ComputeStringOps.isNullOrEmpty(request.getOutput())) {
            nameMap.put("#o", BINARY_RESULT);
            valueMap.put(":o", new AttributeValue().withB(compressString(request.getOutput())));
            updateExpression += ", #o = :o";
        }


        if (request.getStatus().isTerminalState()) {
            // This should have been milliseconds
            final long jobCompletionTime = fillCurrentTimeSeconds();
            nameMap.put("#jft", JOB_FINISHED_TIME);
            nameMap.put("#ttl", TTL);
            valueMap.put(":jft", new AttributeValue().withN(Long.toString(jobCompletionTime)));
            valueMap.put(":ttl", new AttributeValue().withN(Long.toString(fillJobTtlSeconds())));
            updateExpression += ", #jft = :jft, #ttl = :ttl";
        }

        if (!ComputeStringOps.isNullOrEmpty(request.getStepExecutionArn())) {
            nameMap.put(SEARN_VALUE, STEP_EXECUTION_ARNS);
            valueMap.put(SEARN_FIELD, new AttributeValue().withL(new AttributeValue(request.getStepExecutionArn())));
            updateExpression += ", #searn = list_append(if_not_exists(#searn, :empty_list), :searn)";
        }

        // Leave REMOVE at the end; it is not part of the set "x=y" expression
        if (!ComputeStringOps.isNullOrEmpty(request.getOutput())) {
            nameMap.put("#dr", RESULT);
            updateExpression += " REMOVE #dr";
        }

        final String conditionExpression = makeString(getJobIdExistsCondition(),
                " AND (:st = :stpro OR NOT #st in (:stcom, :stcan, :st))");

        log.info(makeString("Updating job id ", request.getJobId(), " to status ", request.getStatus().toString()));

        final Map<String, AttributeValue> jobIdKey = primaryKeyMap(JOB_ID, request.getJobId());

        return new Update()
                .withTableName(dynamoDbJobsTable)
                .withKey(jobIdKey)
                .withUpdateExpression(updateExpression)
                .withExpressionAttributeNames(nameMap)
                .withExpressionAttributeValues(valueMap.build())
                .withConditionExpression(conditionExpression);
    }

    private Delete deleteIdempotentJobAction(final String idempotencyId) {
        return new Delete()
                .withTableName(dynamoDbIdempotencyJobsTable)
                .withKey(primaryKeyMap(IDEMPOTENCY_ID, idempotencyId));
    }

    private void updateActiveJobsCountDirect(final String userServiceWorker, final boolean isIncrement) throws ComputeException {
        final int diff = isIncrement ? 1 : -1;
        final String updateExpression = "ADD #ct :diff SET #ttl = :ttl";

        final NameMap nameMap = new NameMap().with("#ct", COUNT).with("#ttl", TTL);

        final ValueMap expressionValues = new ValueMap()
                .withInt(":diff", diff)
                .withLong(":ttl", ActiveJobsCountDBRecord.getTtlSeconds());

        final UpdateItemSpec updateItemSpec = new UpdateItemSpec();

        if (diff < 0) {
            expressionValues.withInt(":zero", 0);
            updateItemSpec.withConditionExpression("#ct > :zero");
        }

        updateItemSpec
                .withPrimaryKey(USER_SERVICE_WORKER, userServiceWorker)
                .withUpdateExpression(updateExpression)
                .withNameMap(nameMap)
                .withValueMap(expressionValues);

        updateItemSpec(dynamoDbActiveJobsCountTable, updateItemSpec, makeString("job count += ", diff),
                "Failed to update active jobs count.");
    }

    // Action for transaction
    private Update updateActiveJobsCountAction(final String userServiceWorker, final boolean isIncrement) {
        final int diff = isIncrement ? 1 : -1;
        final String updateExpression = "ADD #ct :diff SET #ttl = :ttl";

        final NameMap nameMap = new NameMap().with("#ct", COUNT).with("#ttl", TTL);

        final Map<String, AttributeValue> key = primaryKeyMap(USER_SERVICE_WORKER, userServiceWorker);

        final ImmutableMap.Builder<String, AttributeValue> expressionValues = ImmutableMap.<String, AttributeValue>builder()
                .put(":diff", new AttributeValue().withN(Integer.toString(diff)))
                .put(":ttl", new AttributeValue().withN(Long.toString(ActiveJobsCountDBRecord.getTtlSeconds())));

        final Update update = new Update()
                .withTableName(dynamoDbActiveJobsCountTable)
                .withKey(key)
                .withUpdateExpression(updateExpression)
                .withExpressionAttributeNames(nameMap);
        if (diff < 0) {
            expressionValues.put(":zero", new AttributeValue().withN(Integer.toString(0)));
            update.setConditionExpression("#ct > :zero");
        }
        return update.withExpressionAttributeValues(expressionValues.build());
    }

    private Map<String, AttributeValue> primaryKeyMap(final String fieldName, final String key) {
        return ImmutableMap.<String, AttributeValue>builder().put(fieldName, new AttributeValue().withS(key)).build();
    }

    private String getJobIdExistsCondition() {
        return String.format("attribute_exists(%s)", JOB_ID);
    }
}
