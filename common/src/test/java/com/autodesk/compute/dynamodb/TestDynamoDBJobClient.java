package com.autodesk.compute.dynamodb;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMappingException;
import com.amazonaws.services.dynamodbv2.document.BatchWriteItemOutcome;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.model.*;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.Failure;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient.HeartbeatStatus;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.model.dynamodb.QueueInfo;
import com.autodesk.compute.model.dynamodb.SearchJobsResult;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.categories.UnitTests;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import jakarta.ws.rs.NotFoundException;
import java.util.*;
import java.util.concurrent.*;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.configuration.ComputeConstants.DynamoFields.*;
import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Category(UnitTests.class)
@Slf4j
public class TestDynamoDBJobClient {
    private static final String testPayload = "{\n" +
            "    \"greeting\": \"Hello, May Savage! You have 2 unread messages.\",\n" +
            "    \"favoriteFruit\": \"strawberry\"\n" +
            "  }";

    @Spy
    private static DynamoDBJobClient dynamoDBJobClient;

    private static DynamoDBIdempotentJobsClient dynamoDBIdempotentJobsClient;
    private static DynamoDBActiveJobsCountClient dynamoDBActiveJobsCountClient;

    private static AmazonDynamoDB amazonDynamoDBClient;
    private static DynamoDB dynamoDB;
    private static ComputeConfig computeConfig;
    private static ArrayList<String> tags;
    private static Table jobTable;
    private static Table idempotentJobsTable;
    private static Table activeJobsCountTable;

    private String jobId;
    private String oxygenToken;
    private String userId;
    private String idempotencyId;

    @ClassRule
    public static final LocalDynamoDBCreationRule localDbCreator = new LocalDynamoDBCreationRule();

    @BeforeClass
    public static void beforeClass() {
        // set config values
        tags = new ArrayList<>();
        tags.add("test");
        tags.add("completion");

        // build the DB, create job table
        amazonDynamoDBClient = localDbCreator.getAmazonDynamoDB();
        assertNotNull(amazonDynamoDBClient);
        dynamoDBJobClient = new DynamoDBJobClient(amazonDynamoDBClient);
        dynamoDBIdempotentJobsClient = new DynamoDBIdempotentJobsClient(amazonDynamoDBClient);
        dynamoDBActiveJobsCountClient = new DynamoDBActiveJobsCountClient(amazonDynamoDBClient);
        assertNotNull(dynamoDBJobClient);
        dynamoDB = dynamoDBJobClient.getDynamoDB();
        assertNotNull(dynamoDB);
    }

    @Before
    public void beforeTest() {
        MockitoAnnotations.openMocks(this);
    }

    @After
    public void afterTest() {
        destroyJobTable();
        destroyIdempotentJobsTable();
    }

    private JobDBRecord buildJobRecord() {
        // build a job record
        final JobDBRecord jobRecord = JobDBRecord.createDefault(jobId);
        jobRecord.setTags(tags);
        jobRecord.setPercentComplete(0);
        jobRecord.setBinaryPayload(ComputeStringOps.compressString(testPayload));
        jobRecord.setOxygenToken(oxygenToken);
        jobRecord.setHeartbeatStatus(HeartbeatStatus.ENABLED.toString());
        jobRecord.setHeartbeatTimeoutSeconds(120);
        jobRecord.setCurrentRetry(0);
        jobRecord.setLastHeartbeatTime(0L);
        jobRecord.setService(computeConfig.getAppMoniker());
        jobRecord.setWorker("worker");
        jobRecord.setJobFinishedTime(0L);
        jobRecord.setServiceClientId(ComputeStringOps.makeServiceClientId(userId, computeConfig.getOxygenClientId(), computeConfig.getAppMoniker()));
        jobRecord.setIdempotencyId(idempotencyId);
        return jobRecord;
    }

    private JobDBRecord buildJobRecord(final String jobId, final String userId) {
        // build a job record
        final JobDBRecord jobRecord = JobDBRecord.createDefault(jobId);
        jobRecord.setTags(tags);
        jobRecord.setPercentComplete(0);
        jobRecord.setOxygenToken(oxygenToken);
        jobRecord.setHeartbeatStatus(HeartbeatStatus.ENABLED.toString());
        jobRecord.setHeartbeatTimeoutSeconds(120);
        jobRecord.setCurrentRetry(0);
        jobRecord.setLastHeartbeatTime(0L);
        jobRecord.setService(computeConfig.getAppMoniker());
        jobRecord.setWorker("worker");
        jobRecord.setJobFinishedTime(0L);
        jobRecord.setServiceClientId(ComputeStringOps.makeServiceClientId(userId, computeConfig.getOxygenClientId(), computeConfig.getAppMoniker()));
        jobRecord.setIdempotencyId(idempotencyId);
        jobRecord.setUserId(userId);
        jobRecord.setUserType("Student");
        return jobRecord;
    }

    private static void buildJobTable() {
        if (jobTable == null) {
            // get compute config
            computeConfig = ComputeConfig.getInstance();
            assertNotNull(computeConfig);
            try {
                final CreateTableRequest request = new CreateTableRequest()
                        .withTableName(computeConfig.getDbProgressTable())
                        .withProvisionedThroughput(new ProvisionedThroughput(1000L, 1000L))
                        .withAttributeDefinitions(
                                new AttributeDefinition(JOB_ID, ScalarAttributeType.S),
                                new AttributeDefinition(SERVICE_CLIENT_ID, ScalarAttributeType.S),
                                new AttributeDefinition(MODIFICATION_TIME, ScalarAttributeType.N),
                                new AttributeDefinition(QUEUE_ID, ScalarAttributeType.S),
                                new AttributeDefinition(JOB_ENQUEUE_TIME, ScalarAttributeType.N),
                                new AttributeDefinition(ARRAY_JOB_ID, ScalarAttributeType.S),
                                new AttributeDefinition(ARRAY_INDEX, ScalarAttributeType.N))
                        .withKeySchema(
                                new KeySchemaElement(JOB_ID, KeyType.HASH))
                        .withGlobalSecondaryIndexes(
                                new GlobalSecondaryIndex().withIndexName(computeConfig.getDbProgressTableSearchIndex())
                                        .withKeySchema(new KeySchemaElement(SERVICE_CLIENT_ID, KeyType.HASH),
                                                new KeySchemaElement(MODIFICATION_TIME, KeyType.RANGE))
                                        .withProvisionedThroughput(new ProvisionedThroughput(1000L, 1000L))
                                        .withProjection(new Projection().withProjectionType(ProjectionType.ALL)))
                        .withGlobalSecondaryIndexes(
                                new GlobalSecondaryIndex().withIndexName(computeConfig.getDbProgressTableQueueingIndex())
                                        .withKeySchema(new KeySchemaElement(QUEUE_ID, KeyType.HASH),
                                                new KeySchemaElement(JOB_ENQUEUE_TIME, KeyType.RANGE))
                                        .withProvisionedThroughput(new ProvisionedThroughput(1000L, 1000L))
                                        .withProjection(new Projection().withProjectionType(ProjectionType.ALL)))
                        .withGlobalSecondaryIndexes(
                                new GlobalSecondaryIndex().withIndexName(computeConfig.getDbProgressTableArrayJobIndex())
                                        .withKeySchema(new KeySchemaElement(ARRAY_JOB_ID, KeyType.HASH),
                                                new KeySchemaElement(ARRAY_INDEX, KeyType.RANGE))
                                        .withProvisionedThroughput(new ProvisionedThroughput(1000L, 1000L))
                                        .withProjection(new Projection().withProjectionType(ProjectionType.ALL)))
                        .withSSESpecification(new SSESpecification().withEnabled(true));

                jobTable = dynamoDB.createTable(request);
                final TableDescription description = jobTable.waitForActive();
                log.info("buildJobTable succeeded. Table name: {}", description.getTableName());
                log.info("buildJobTable succeeded. Table status: {}", description.getTableStatus());
                log.info("buildJobTable succeeded. Table KeySchema: {}", description.getKeySchema().toString());
                log.info("buildJobTable succeeded. Table GSI: {}", description.getGlobalSecondaryIndexes().toString());
            } catch (final Exception e) {
                fail("buildJobTable caught exception: " + e);
            }
        }
    }

    private static void buildIdempotentJobsTable() {
        if (idempotentJobsTable == null) {
            // get compute config
            computeConfig = ComputeConfig.getInstance();
            assertNotNull(computeConfig);
            try {
                final CreateTableRequest request = new CreateTableRequest()
                        .withTableName(computeConfig.getIdempotentJobsTable())
                        .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
                        .withAttributeDefinitions(
                                new AttributeDefinition(IDEMPOTENCY_ID, ScalarAttributeType.S),
                                new AttributeDefinition(JOB_ID, ScalarAttributeType.S))
                        .withKeySchema(new KeySchemaElement(IDEMPOTENCY_ID, KeyType.HASH))
                        .withGlobalSecondaryIndexes(
                                new GlobalSecondaryIndex().withIndexName("JobIdIndex")
                                        .withKeySchema(new KeySchemaElement(JOB_ID, KeyType.HASH))
                                        .withProvisionedThroughput(new ProvisionedThroughput(10L, 10L))
                                        .withProjection(new Projection().withProjectionType(ProjectionType.ALL)))
                        .withSSESpecification(new SSESpecification().withEnabled(true));

                idempotentJobsTable = dynamoDB.createTable(request);
                final TableDescription description = idempotentJobsTable.waitForActive();
                log.info("buildIdempotentJobsTable succeeded. Table name: {}", description.getTableName());
                log.info("buildIdempotentJobsTable succeeded. Table status: {}", description.getTableStatus());
                log.info("buildIdempotentJobsTable succeeded. Table KeySchema: {}", description.getKeySchema().toString());
                log.info("buildIdempotentJobsTable succeeded. Table GSI: {}", description.getGlobalSecondaryIndexes().toString());
            } catch (final Exception e) {
                fail("buildIdempotentJobsTable caught exception: " + e);
            }
        }
    }

    private static void buildActiveJobsCountTable() {
        if (activeJobsCountTable == null) {
            // get compute config
            computeConfig = ComputeConfig.getInstance();
            assertNotNull(computeConfig);
            try {
                final CreateTableRequest request = new CreateTableRequest()
                        .withTableName(computeConfig.getActiveJobsCountTable())
                        .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
                        .withAttributeDefinitions(new AttributeDefinition(USER_SERVICE_WORKER, ScalarAttributeType.S))
                        .withKeySchema(new KeySchemaElement(USER_SERVICE_WORKER, KeyType.HASH))
                        .withSSESpecification(new SSESpecification().withEnabled(true));

                activeJobsCountTable = dynamoDB.createTable(request);
                final TableDescription description = activeJobsCountTable.waitForActive();
                log.info("buildActiveJobsCountTable succeeded. Table name: {}", description.getTableName());
                log.info("buildActiveJobsCountTable succeeded. Table status: {}", description.getTableStatus());
                log.info("buildActiveJobsCountTable succeeded. Table KeySchema: {}", description.getKeySchema().toString());
            } catch (final Exception e) {
                fail("buildActiveJobsCountTable caught exception: " + e);
            }
        }
    }

    private static void destroyJobTable()  {
        if (jobTable != null) {
            try {
                jobTable.delete();
                jobTable.waitForDelete();
            } catch (final InterruptedException e) {
                // do nothing
            }
        }
        jobTable = null;
    }

    private static void destroyIdempotentJobsTable()  {
        if (idempotentJobsTable != null) {
            try {
                idempotentJobsTable.delete();
                idempotentJobsTable.waitForDelete();
            } catch (final InterruptedException e) {
                // do nothing
            }
        }
        idempotentJobsTable = null;
    }

    private void sleep25ms() {
        try {
            //sleep a moment to ensure we have a time different than job creation
            Thread.sleep(25);
        } catch (final Throwable e) {
            log.error("Error during sleep", e); // ignore
        }
    }

    private void updateItemExpectException() {
        try {
            final UpdateItemSpec updateItemSpec = new UpdateItemSpec();
            dynamoDBJobClient.updateItemSpec(computeConfig.getDbProgressTable(), updateItemSpec, jobId, "Exception expected");
            fail("Update item did not threw an exception");
        } catch (final ComputeException e) {
            assertNotNull(e.getMessage(), e.getCode());
        }
    }

    private void updateProgressNoException() {
        try {
            final Integer percentComplete = 100;
            final String details = "no details";
            dynamoDBJobClient.updateProgress(jobId, percentComplete, details);
            final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
            assertEquals("Job details should have been updated", details, job.findDetails());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }
    }

    private void updateProgressException() {
        try {
            dynamoDBJobClient.setHeartbeatStatus(jobId, HeartbeatStatus.DISABLED);
        } catch (final ComputeException e) {
            fail("Changing heartbeat status failed: " + e);
        }
        try {
            final Integer percentComplete = 100;
            final String details = "no details";
            dynamoDBJobClient.updateProgress(jobId, percentComplete, details);
        } catch (final ComputeException e) {
            assertNotNull(e.getMessage(), e.getCode());
        } finally {
            try {
                dynamoDBJobClient.setHeartbeatStatus(jobId, HeartbeatStatus.ENABLED);
            } catch (final ComputeException e) {
                fail("Restoring heartbeat to enabled failed: " + e);
            }
        }
    }



    private void updateHeartbeatNoException() {
        try {
            dynamoDBJobClient.updateHeartbeat(jobId);
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }
    }

    private void updateHeartbeatException() {
        try {
            dynamoDBJobClient.setHeartbeatStatus(jobId, HeartbeatStatus.DISABLED);
        } catch (final ComputeException e) {
            fail("Changing heartbeat status failed: " + e);
        }
        try {
            dynamoDBJobClient.updateHeartbeat(jobId);
            fail("Heartbeat update should have failed");
        } catch (final ComputeException e) {
            assertNotNull(e.getMessage(), e.getCode());
        } finally {
            try {
                dynamoDBJobClient.setHeartbeatStatus(jobId, HeartbeatStatus.ENABLED);
            } catch (final ComputeException e) {
                fail("Restoring heartbeat to enabled failed: " + e);
            }
        }
    }

    private void updateNoTableConfig() {
        jobTable = null;
        // no table
        jobId = "job00";
        oxygenToken = "oxygenToken00";
        userId = "user00";
        computeConfig = ComputeConfig.getInstance();
    }

    private void updateNoJobConfig() {
        buildJobTable();
        jobId = "job01";
        oxygenToken = "oxygenToken01";
        userId = "user01";
    }

    @Test
    public void testNoJobsNoTableUpdateToken() {
        updateNoTableConfig();
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> dynamoDBJobClient.updateToken(jobId, "differentToken0")
        );
    }

    @Test
    public void testNoJobsNoTableUpdateTags() {
        updateNoTableConfig();
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> dynamoDBJobClient.updateTags(jobId, Arrays.asList("test-tag"))
        );
    }

    @Test
    public void testNoJobsNoTableGetJob() {
        updateNoTableConfig();
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> dynamoDBJobClient.getJob(jobId)
        );

        // updateItemSpec
        updateItemExpectException();
    }

    @Test
    public void testNoJobsNoTableDeleteJob() {
        updateNoTableConfig();
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> dynamoDBJobClient.deleteJob(jobId)
        );
    }

    @Test
    public void testNoJobsNoTableInsertNullJob() {
        updateNoTableConfig();
        final JobDBRecord record = new JobDBRecord();
        validateThrows(
                DynamoDBMappingException.class, null,
                () -> dynamoDBJobClient.insertJob(record)
        );
    }

    @Test
    public void testNoJobsNoTableUpdateProgress() {
        updateNoTableConfig();
        final Integer percentComplete = 100;
        final String details = "no details";
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> dynamoDBJobClient.updateProgress(jobId, percentComplete, details)
        );
    }

    @Test
    public void testNoJobsNoTableUpdateJobCompletion() {
        updateNoTableConfig();
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.AWS_INTERNAL_ERROR),
                () -> dynamoDBJobClient.markJobCompleted(jobId, null, "OK", null)
        );
    }

    @Test
    public void testNoJobsWithTableDeleteJob() throws ComputeException {
        updateNoJobConfig();
        dynamoDBJobClient.deleteJob(jobId);
        assertEquals(null, dynamoDBJobClient.getJob(jobId));
    }

    @Test
    public void testNoJobsWithTableUpdateToken() throws ComputeException{
        updateNoJobConfig();
        dynamoDBJobClient.updateToken(jobId, "differentToken0");
        assertEquals(1L, dynamoDBJobClient.getJobCount().longValue());
    }

    @Test
    public void testNoJobsWithTableUpdateTags() throws ComputeException{
        updateNoJobConfig();
        dynamoDBJobClient.updateTags(jobId, Arrays.asList("test-tag"));
        assertEquals(1L, dynamoDBJobClient.getJobCount().longValue());
    }

    @Test
    public void testNoJobsWithTableInsertJob() {
        updateNoJobConfig();
        final JobDBRecord record = new JobDBRecord();
        validateThrows(
                DynamoDBMappingException.class, null,
                () -> dynamoDBJobClient.insertJob(record)
        );
    }

    @Test
    public void testNoJobsWithTableUpdateHeartbeat() {
        updateNoJobConfig();
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN),
                () -> dynamoDBJobClient.updateHeartbeat(jobId)
        );
    }

    @Test
    public void testNoJobsWithTableUpdateProgress() {
        updateNoJobConfig();
        final Integer percentComplete = 100;
        final String details = "no details";
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN),
                () -> dynamoDBJobClient.updateProgress(jobId, percentComplete, details)
        );
    }

    @Test
    public void testNoJobsWithTableGetJob() throws ComputeException {
        updateNoJobConfig();
        final JobDBRecord jobRecord = dynamoDBJobClient.getJob(jobId);
        assertEquals(null, jobRecord);
    }

    @Test
    public void testWithJobs() throws ComputeException {
        buildJobTable();
        oxygenToken = "oxygenToken11";
        userId = "user11";
        final String serviceClientId = ComputeStringOps.makeServiceClientId(userId, computeConfig.getOxygenClientId(), computeConfig.getAppMoniker());

        final Integer totalJobs = 150;
        long creationTime = 0L;
        try {
            for (int i = 0; i < totalJobs; i++) {
                jobId = String.format("job11.%03d", i);
                dynamoDBJobClient.insertJob(buildJobRecord());
            }
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // getJobCount
        try {
            assertEquals(totalJobs.longValue(), dynamoDBJobClient.getJobCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        jobId = "job11.000";

        // make sure TTL is not set on newly created jobs.
        try {
            final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
            creationTime = job.getModificationTime();
            log.info(ComputeStringOps.makeString("creationTime is ", Long.toString(creationTime), " for service ", job.getService()));
            assertNotEquals("modificationTime should be set on just created job", 0L, creationTime);
            assertNotEquals("TTL should be set on just created job", 0L, job.getTtl().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // make sure payload is the same in the DB
        try {
            final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
            final String payload = job.findPayload();
            assertEquals("payload should be equal to testPayload", testPayload, payload);
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // updateProgress
        updateProgressNoException();

        // updateHeartbeat
        updateHeartbeatNoException();

        // Block heartbeat, verify that the update fails
        updateHeartbeatException();

        // Block heartbeat, verify that the job progress fails
        updateProgressException();

        sleep25ms();

        // Modification time should be set on job progress change
        long progressModificationTime = 0L;
        try {
            final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
            progressModificationTime = job.getModificationTime();
            log.info(makeString("progressModificationTime is ", Long.toString(progressModificationTime), " for service ", job.getService()));
            assertNotEquals("Modification time not set", 0L, progressModificationTime);
            assertNotEquals("Modification time did not change", creationTime, progressModificationTime);
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // updateJobStatus
        try {
            dynamoDBJobClient.markJobInprogress(jobId);
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // Status should be INPROGRESS
        try {
            final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
            final String status = job.getStatus();
            assertTrue(status.equals(JobStatus.INPROGRESS.toString()));
            assertTrue(status.equals("INPROGRESS"));
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // search by time - expecting to find a job
        try {
            final List<String> jobsFromWhenWeStarted = new ArrayList<>();
            final long utcTimeTo = new Date().getTime(); //now
            final List<JobDBRecord> response = dynamoDBJobClient.getJobsByTime(serviceClientId, totalJobs + 1, creationTime, utcTimeTo, Optional.empty());
            assertNotNull(response);
            log.info("response has " + Long.toString(response.size()) + " entries: " + response.get(0).toString());

            final Iterator<JobDBRecord> iterator = response.iterator();
            assertNotNull(iterator);
            if (iterator.hasNext()) {
                while (iterator.hasNext()) {
                    final JobDBRecord jobRecord = iterator.next();
                    assertNotNull(jobRecord);
                    assertNotNull("No " + JOB_ID + " in item: " + jobRecord.toString() + ", response has " + Long.toString(response.size()) + " entries", jobRecord.getJobId());
                    if (jobRecord.getJobId() != null) {
                        jobsFromWhenWeStarted.add(jobRecord.getJobId());
                    }
                }
            } else {
                log.error(makeString("getJobsFromTime could not find jobs in time windows ",
                        Long.toString(creationTime), " and ", Long.toString(utcTimeTo), " (range is ", Long.toString(utcTimeTo - creationTime), "ms)"));
            }

            assertNotNull("getJobsByTime returned a null object", jobsFromWhenWeStarted);
            assertEquals("getJobsByTime returned the wrong number of jobs", totalJobs.longValue(), jobsFromWhenWeStarted.size());
        } catch (final Throwable e) {
            fail("Exception not expected: " + e);
        }

        final String endResult = "OK";
        // updateJobCompletion
        try {
            dynamoDBJobClient.markJobCompleted(jobId, null, "OK", null);
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // make sure result is the same in the DB
        try {
            final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
            assertEquals("result should be OK", endResult, job.findResult());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        sleep25ms();

        // Modification time should be set on job completion change
        long newModificationTime = 0L;
        try {
            final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
            newModificationTime = job.getModificationTime();
            log.info(makeString("newModificationTime is ", Long.toString(newModificationTime), " for service ", job.getService()));
            assertNotEquals("Modification time not set", 0L, newModificationTime);
            assertNotEquals("Modification time did not change", progressModificationTime, newModificationTime);
            final String status = job.getStatus();
            assertTrue(JobStatus.valueOf(status).isCompleted());
            assertTrue(status.equals(JobStatus.COMPLETED.toString()));
            assertTrue("COMPLETED".equals(status));
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // make sure TTL is set on finished job.
        try {
            final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
            assertNotEquals("TTL should be set on finished job", 0L, job.getTtl().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // updateToken
        try {
            dynamoDBJobClient.updateToken(jobId, "differentToken0");
            assertEquals("differentToken0", dynamoDBJobClient.getToken(jobId));
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // updateTags
        try {
            final List<String> newTags = Arrays.asList("test-tag");
            dynamoDBJobClient.updateTags(jobId, newTags);
            assertEquals(newTags, dynamoDBJobClient.getTags(jobId));
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // search by time, using the paging API - get jobs in a single page
        try {
            final long utcTimeTo = new Date().getTime(); //now
            final Integer maxResults = totalJobs + 1;
            final Map<String, Object> response = dynamoDBJobClient.getJobsByTimeForPage(serviceClientId, null, maxResults, creationTime, utcTimeTo, Optional.empty());

            // first (and only) page
            assertNotNull(response);
            assertNotNull(response.get(DynamoDBJobClient.JOBS_FIELD));
            assertTrue(response.get(DynamoDBJobClient.JOBS_FIELD) instanceof List);
            final List<JobDBRecord> jobs = (List<JobDBRecord>) response.get(DynamoDBJobClient.JOBS_FIELD);
            assertEquals(totalJobs.intValue(), jobs.size());
            log.info(makeString("getJobsByTimeForPage returned ", jobs.size(), " results for first page"));
            assertNull(response.get(DynamoDBJobClient.NEXT_TOKEN_FIELD));
        } catch (final Throwable e) {
            fail("Exception not expected: " + e);
        }

        // search by time, using the paging API - get jobs in two pages
        try {
            final long utcTimeTo = new Date().getTime(); //now
            final Integer maxResults = 100;
            Map<String, Object> response = dynamoDBJobClient.getJobsByTimeForPage(serviceClientId, null, maxResults, creationTime, utcTimeTo, Optional.empty());

            // first page
            assertNotNull(response);
            assertNotNull(response.get(DynamoDBJobClient.JOBS_FIELD));
            List<JobDBRecord> jobs = (List<JobDBRecord>) response.get(DynamoDBJobClient.JOBS_FIELD);
            assertEquals(maxResults.intValue(), jobs.size());
            log.info(makeString("getJobsByTimeForPage returned ", Long.toString(jobs.size()), " results for first page"));

            // use pagination token to retrieve second page
            assertNotNull(response.get(DynamoDBJobClient.NEXT_TOKEN_FIELD));
            final String nextToken = response.get(DynamoDBJobClient.NEXT_TOKEN_FIELD).toString();
            response = dynamoDBJobClient.getJobsByTimeForPage(serviceClientId, nextToken, maxResults, creationTime, utcTimeTo, Optional.empty());
            response = dynamoDBJobClient.getJobsByTimeForPage(serviceClientId, nextToken, maxResults, creationTime, utcTimeTo, Optional.empty());

            // second page
            assertNotNull(response);
            assertNotNull(response.get(DynamoDBJobClient.JOBS_FIELD));
            jobs = (List<JobDBRecord>) response.get(DynamoDBJobClient.JOBS_FIELD);
            assertEquals(totalJobs - maxResults, jobs.size());
            log.info(makeString("getJobsByTimeForPage returned ", Long.toString(jobs.size()), " results for second page"));
            assertNull(response.get(DynamoDBJobClient.NEXT_TOKEN_FIELD));
        } catch (final Throwable e) {
            fail("Exception not expected: " + e);
        }

        // search by time - expecting to NOT find a job
        try {
            final List<String> jobsInTheMomentBeforeWeStarted = new ArrayList<>();
            final Long utcTimeFrom = creationTime - 200;
            final Long utcTimeTo = creationTime - 100;
            final List<JobDBRecord> response = dynamoDBJobClient.getJobsByTime(serviceClientId, 100, utcTimeFrom, utcTimeTo, Optional.empty());
            final Iterator<JobDBRecord> iterator = response.iterator();
            if (iterator.hasNext()) {
                while (iterator.hasNext()) {
                    final JobDBRecord item = iterator.next();
                    jobsInTheMomentBeforeWeStarted.add(item.getJobId());
                }
            }
            assertNotNull("getJobsByTime returned a null object", jobsInTheMomentBeforeWeStarted);
            assertEquals("getJobsByTime returned a populated collection", 0, jobsInTheMomentBeforeWeStarted.size());
            log.info(makeString("getJobsByTime returned ", Long.toString(jobsInTheMomentBeforeWeStarted.size()), " jobs, we expected 0"));
        } catch (final Throwable e) {
            fail("Exception not expected: " + e);
        }

        // search by time, using the paging API - expecting to NOT find a job
        try {
            final Long utcTimeFrom = creationTime;
            final Long utcTimeTo = new Date().getTime(); //now

            final Map<String, Object> response = dynamoDBJobClient.getJobsByTimeForPage(serviceClientId, null, 100, utcTimeFrom, utcTimeTo, Optional.empty());

            log.info(makeString("getJobsByTimeForPage returned ", response));

            assertNotNull(response);
            assertNotNull(response.get(DynamoDBJobClient.JOBS_FIELD));
            final List<JobDBRecord> jobs = (List<JobDBRecord>) response.get(DynamoDBJobClient.JOBS_FIELD);
            assertEquals(100, jobs.size());
            assertNotNull(response.get(DynamoDBJobClient.NEXT_TOKEN_FIELD));
        } catch (final Throwable e) {
            fail("Exception not expected: " + e);
        }

        // search by time, using the paging API - expecting to return a null object
        try {
            final long utcTimeTo = new Date().getTime(); //now

            // use the paging API with a bad token
            final String nextToken = ComputeStringOps.makeNextToken("foo", utcTimeTo, "bar");
            assertNotNull(dynamoDBJobClient.getJobsByTimeForPage(serviceClientId, nextToken, 100, creationTime, utcTimeTo, Optional.empty()));
        } catch (final Exception e) {
            fail("Exception not expected: " + e);
        }

        // Try invalid token and expect and error
        final Map<String, Object> response = dynamoDBJobClient.getJobsByTimeForPage(serviceClientId, "gibrish", 100, creationTime, creationTime, Optional.empty());
        assertNotNull(response.get(DynamoDBJobClient.ERROR_FIELD));

        // deleteJob
        try {
            dynamoDBJobClient.deleteJob(jobId);
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // getJobCount
        try {
            assertEquals(149L, dynamoDBJobClient.getJobCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

    }

    @Test
    public void testCanceledJobsSuccessPath() {
        buildJobTable();
        jobId = "job11";
        oxygenToken = "oxygenToken11";

        try {
            dynamoDBJobClient.insertJob(buildJobRecord());
            // getJobCount
            assertEquals(1L, dynamoDBJobClient.getJobCount().longValue());
            JobDBRecord job = dynamoDBJobClient.getJob(jobId);
            assertNotEquals("TTL should be set on just created job", 0L, job.getTtl().longValue());
            assertEquals(JobStatus.SCHEDULED.toString(), job.getStatus());
            dynamoDBJobClient.markJobInprogress(jobId);
            updateProgressNoException();
            dynamoDBJobClient.updateToken(jobId, "differentToken0");
            assertEquals("differentToken0", dynamoDBJobClient.getToken(jobId));
            dynamoDBJobClient.updateTags(jobId, Arrays.asList("test-tag"));
            assertEquals(Arrays.asList("test-tag"), dynamoDBJobClient.getTags(jobId));
            dynamoDBJobClient.markJobInprogress(jobId);
            updateProgressNoException();
            dynamoDBJobClient.markJobCanceled(jobId, null, null);
            job = dynamoDBJobClient.getJob(jobId);
            assertNotEquals("TTL should be set on finished job", 0L, job.getTtl().longValue());
            if (job != null) {
                final List<String> expectedStatusUpdates = Arrays.asList(
                        JobStatus.SCHEDULED.toString(),
                        JobStatus.INPROGRESS.toString(),
                        JobStatus.INPROGRESS.toString(),
                        JobStatus.CANCELED.toString());
                assertEquals(job.getStatusUpdates().size(), expectedStatusUpdates.size());
                for (int i = 0; i < expectedStatusUpdates.size(); i++) {
                    assertTrue(job.getStatusUpdates().get(i).contains(expectedStatusUpdates.get(i)));
                }
            }
            assertEquals(1L, dynamoDBJobClient.getJobCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }
    }

    @Test
    public void testFailedJobSuccessPath() throws ComputeException {
        buildJobTable();
        jobId = "job11";
        oxygenToken = "oxygenToken11";

        // insertJob
        dynamoDBJobClient.insertJob(buildJobRecord());
        JobDBRecord job = dynamoDBJobClient.getJob(jobId);
        //Validate that we have non null and empty Error list
        assertNotNull(job.getErrors());
        assertEquals(0L, job.getErrors().size());

        // getJobCount
        assertEquals(1L, dynamoDBJobClient.getJobCount().longValue());

        updateProgressNoException();

        final List<Failure> failures = new ArrayList<>();
        final Failure failure = new Failure();
        failure.setError("reason: error1");
        failures.add(failure);
        dynamoDBJobClient.markJobFailed(jobId, null, failures, "FAILED", null);
        job = dynamoDBJobClient.getJob(jobId);

        //Validate that we the error captured in database.
        assertEquals(1L, job.getErrors().size());

        // Validate the Status update List contains exacted updates in correct order.
        if (job != null) {
            final List<String> expectedStatusUpdates = Arrays.asList(JobStatus.SCHEDULED.toString(), JobStatus.FAILED.toString());
            assertEquals(job.getStatusUpdates().size(), expectedStatusUpdates.size());
            for (int i = 0; i < expectedStatusUpdates.size(); i++) {
                assertTrue(job.getStatusUpdates().get(i).contains(expectedStatusUpdates.get(i)));
            }
        }

        // getJobCount
        assertEquals(1L, dynamoDBJobClient.getJobCount().longValue());
    }

    @Test
    public void testFailedJobAddedFailure() throws ComputeException {
        buildJobTable();
        jobId = "job11";
        oxygenToken = "oxygenToken11";

        dynamoDBJobClient.insertJob(buildJobRecord());

        final List<Failure> failures = new ArrayList<>();
        final Failure failure = new Failure();
        failure.setError("reason: error1");
        failures.add(failure);
        dynamoDBJobClient.markJobFailed(jobId, null, failures, "FAILED", null);
        final JobDBRecord job = dynamoDBJobClient.getJob(jobId);

        final List<Failure> failuresAgain = new ArrayList<>();
        final Failure failurenew = new Failure();
        failurenew.setError("reason: error2");
        failuresAgain.add(failure);

        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.JOB_UPDATE_FORBIDDEN),
                () -> dynamoDBJobClient.markJobFailed(jobId, null, failuresAgain, "FAILED", null)
        );
        // updating status with same value should fail


        // Check that previous update actually failed and did not add to the status updates list.
        if (job != null) {
            final List<String> expectedStatusUpdates = Arrays.asList(JobStatus.SCHEDULED.toString(), JobStatus.FAILED.toString());
            assertEquals(job.getStatusUpdates().size(), expectedStatusUpdates.size());
            for (int i = 0; i < expectedStatusUpdates.size(); i++) {
                assertTrue(job.getStatusUpdates().get(i).contains(expectedStatusUpdates.get(i)));
            }
        }

    }

    @Test
    public void testJobToken() {
        buildJobTable();

        jobId = "job01";
        oxygenToken = "oxygenToken01";
        userId = "user01";

        // insertJob - null
        try {
            final JobDBRecord record = buildJobRecord();
            dynamoDBJobClient.insertJob(record);
        } catch (final Exception e) {
            fail("Should not have triggered exception while adding job");
        }

        try {
            assertEquals(oxygenToken, dynamoDBJobClient.getToken(jobId));
        } catch (final Exception e) {
            fail("Exception not expected: " + e);
        }

        final String updatedToken = "refreshed token";

        try {
            dynamoDBJobClient.updateToken(jobId, updatedToken);
            assertEquals(updatedToken, dynamoDBJobClient.getToken(jobId));
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }
    }

    @Test
    public void testJobTags() {
        buildJobTable();

        jobId = "job01";

        // insertJob - null
        try {
            final JobDBRecord record = buildJobRecord();
            dynamoDBJobClient.insertJob(record);
        } catch (final Exception e) {
            fail("Should not have triggered exception while adding job");
        }

        try {
            assertEquals(tags, dynamoDBJobClient.getTags(jobId));
        } catch (final Exception e) {
            fail("Exception not expected: " + e);
        }

        final List<String> updatedTags = Arrays.asList("new-tag", "test-tag");
        assertNotEquals(tags, updatedTags);

        try {
            dynamoDBJobClient.updateTags(jobId, updatedTags);
            assertEquals(updatedTags, dynamoDBJobClient.getTags(jobId));
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }
    }

    @Test
    public void testIdempotentJobs() {
        buildJobTable();
        buildIdempotentJobsTable();

        // insertJob
        jobId = "job1";
        oxygenToken = "oxygenToken1";
        idempotencyId = "IdempotencyId1";
        try {
            dynamoDBJobClient.insertJob(buildJobRecord());
            assertEquals(1L, dynamoDBJobClient.getJobCount().longValue());
            assertEquals(1L, dynamoDBIdempotentJobsClient.getItemCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // Insert job with without idempotencyId
        jobId = "job2";
        oxygenToken = "oxygenToken2";
        idempotencyId = null;
        try {
            dynamoDBJobClient.insertJob(buildJobRecord());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }
        // previous insert failed so the counts should be same.
        try {
            assertEquals(2L, dynamoDBJobClient.getJobCount().longValue());
            assertEquals(1L, dynamoDBIdempotentJobsClient.getItemCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // Insert job with same idempotencyId and it should fail
        jobId = "job3";
        oxygenToken = "oxygenToken3";
        idempotencyId = "IdempotencyId1";
        try {
            dynamoDBJobClient.insertJob(buildJobRecord());
            fail("Should trigger exception.");
        } catch (final ComputeException e) {
            assertNotNull(e.getCode());
        }

        // previous insert failed so the counts should be same.
        try {
            assertEquals(2L, dynamoDBJobClient.getJobCount().longValue());
            assertEquals(1L, dynamoDBIdempotentJobsClient.getItemCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // Insert job with different idempotencyId and it should succeed
        jobId = "job4";
        oxygenToken = "oxygenToken4";
        idempotencyId = "IdempotencyId4";
        try {
            dynamoDBJobClient.insertJob(buildJobRecord());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        try {
            assertEquals(3L, dynamoDBJobClient.getJobCount().longValue());
            assertEquals(2L, dynamoDBIdempotentJobsClient.getItemCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // Cancel last job
        try {
            dynamoDBJobClient.markJobCanceled(jobId, idempotencyId, null);
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        try {
            assertEquals(3L, dynamoDBJobClient.getJobCount().longValue());
            assertEquals(1L, dynamoDBIdempotentJobsClient.getItemCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // Since canceling job deletes idempotencyJob, we should be able to create job with same idempotencyId
        jobId = "job5";
        idempotencyId = "IdempotencyId4";
        try {
            dynamoDBJobClient.insertJob(buildJobRecord());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        try {
            assertEquals(4L, dynamoDBJobClient.getJobCount().longValue());
            assertEquals(2L, dynamoDBIdempotentJobsClient.getItemCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // Since fail the last job idempotencyId should be removed
        try {
            final List<Failure> failures = new ArrayList<>();
            dynamoDBJobClient.markJobFailed(jobId, idempotencyId, failures, "output", null);
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        try {
            assertEquals(4L, dynamoDBJobClient.getJobCount().longValue());
            assertEquals(1L, dynamoDBIdempotentJobsClient.getItemCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // New job with same idempotencyId as failed previous failed job should succeed.
        jobId = "job6";
        idempotencyId = "IdempotencyId4";
        try {
            dynamoDBJobClient.insertJob(buildJobRecord());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        try {
            assertEquals(5L, dynamoDBJobClient.getJobCount().longValue());
            assertEquals(2L, dynamoDBIdempotentJobsClient.getItemCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

    }

    @Test
    public void testJobsQueueing() {
        buildJobTable();
        buildIdempotentJobsTable();
        buildActiveJobsCountTable();

        oxygenToken = "oxygenToken11";
        userId = "user11";

        final int totalJobs = 20;

        final List<String> queuedJobIds = new ArrayList<>();

        try {
            for (int i = 0; i < totalJobs; i++) {
                jobId = String.format("jobId.%03d", i);
                final JobDBRecord jobRecord = buildJobRecord(jobId, userId);
                dynamoDBJobClient.insertJob(jobRecord);
                queuedJobIds.add(jobId);
            }
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        final String queueId = buildJobRecord(jobId, userId).getUserServiceWorkerId();

        try {
            for (int i = 0; i < queuedJobIds.size(); i++) {
                final JobDBRecord job = dynamoDBJobClient.getJob(queuedJobIds.get(i));
                assertEquals(job.getQueueId(), job.getUserServiceWorkerId());
                assertNotNull(job.getQueueInfo());
                assertEquals(QueueInfo.StatusEnum.QUEUED, job.getQueueInfo().getStatus());
            }
            // Peek a job and it's status should be PROCESSING and the id should be first from queuedJobIds
            JobDBRecord job = dynamoDBJobClient.peekJob(queueId);
            assertEquals(job.getJobId(), queuedJobIds.get(0));
            assertEquals(QueueInfo.StatusEnum.PROCESSING, job.getQueueInfo().getStatus());

            // Peek again, now we should receive next jobs since previous job was already reserved previous peek.
            job = dynamoDBJobClient.peekJob(queueId);
            assertEquals(job.getJobId(), queuedJobIds.get(1));
            assertEquals(QueueInfo.StatusEnum.PROCESSING, job.getQueueInfo().getStatus());

            // Wait for first peek lease to get expire and we should have that record available again
            // Set a short lease time (default is 30s)
            final int SHORT_LEASE = 1;
            Mockito.doReturn(SHORT_LEASE).when(dynamoDBJobClient).getQueueVisibilityTimeout();
            TimeUnit.MILLISECONDS.sleep((SHORT_LEASE * 1000L) + 10);
            job = dynamoDBJobClient.peekJob(queueId);
            assertEquals(job.getJobId(), queuedJobIds.get(0));
            assertEquals(QueueInfo.StatusEnum.PROCESSING, job.getQueueInfo().getStatus());

            // Remove first job from Queue
            job = dynamoDBJobClient.removeJobFromQueue(job.getJobId(), job.getQueueInfo().getVersion());
            assertEquals(QueueInfo.StatusEnum.REMOVED, job.getQueueInfo().getStatus());
            assertNull(job.getQueueId());

            // Remove second job from Queue
            job = dynamoDBJobClient.dequeueJob(queueId);
            assertEquals(QueueInfo.StatusEnum.REMOVED, job.getQueueInfo().getStatus());
            assertNull(job.getQueueId());

            // Now we have queuedJobs - 2 jobs in queue
            for (int i = 0; i < totalJobs - 2; i++) {
                job = dynamoDBJobClient.dequeueJob(queueId);
                assertEquals(queuedJobIds.get(2 + i), job.getJobId());
                assertEquals(QueueInfo.StatusEnum.REMOVED, job.getQueueInfo().getStatus());
            }

            // Now that queue is empty, peek should receive no job
            job = dynamoDBJobClient.peekJob(queueId);
            assertNull(job);

        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        } catch (final InterruptedException e) {
            fail("Exception not expected: " + e);
        }

        // getJobCount
        try {
            assertEquals(totalJobs, dynamoDBJobClient.getJobCount().longValue());
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

    }

    @Test
    public void testJobsQueueingConcurrency() {
        buildJobTable();
        oxygenToken = "oxygenToken11";
        userId = "user11";

        final int totalJobs = 55;
        final int queuedJobs = 50;

        final List<String> queuedJobIds = new ArrayList<>();

        try {
            for (int i = 0; i < totalJobs - queuedJobs; i++) {
                jobId = String.format("jobId.%03d", i);
                dynamoDBJobClient.insertJob(buildJobRecord());
            }
            // Add these jobs to queue
            for (int i = totalJobs - queuedJobs; i < totalJobs; i++) {
                jobId = String.format("jobId.%03d", i);
                queuedJobIds.add(jobId);
                final JobDBRecord jobRecord = buildJobRecord(jobId, userId);
                dynamoDBJobClient.insertJob(jobRecord);
                dynamoDBJobClient.enqueueJob(jobId, jobRecord.getUserServiceWorkerId());
            }
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        final String queueId = buildJobRecord(jobId, userId).getUserServiceWorkerId();
        final ConcurrentHashMap<String, JobDBRecord> dequeueJobs = new ConcurrentHashMap<>();
        final Runnable r = () -> {
            try {
                JobDBRecord job;
                final Random rand = new Random();
                do {
                    log.info(Thread.currentThread().getName());
                    job = dynamoDBJobClient.peekJob(queueId);
                    if (job != null) {
                        assertEquals(QueueInfo.StatusEnum.PROCESSING, job.getQueueInfo().getStatus());
                        TimeUnit.MICROSECONDS.sleep(rand.nextInt(1_000));
                        job = dynamoDBJobClient.removeJobFromQueue(job.getJobId(), job.getQueueInfo().getVersion());
                        assertEquals(QueueInfo.StatusEnum.REMOVED, job.getQueueInfo().getStatus());
                        assertNull(job.getQueueId());
                        // Make sure we did not receive any duplicate record
                        assertNull(dequeueJobs.put(job.getJobId(), job));
                    }
                } while (job != null);
            } catch (final ComputeException | InterruptedException e) {
                fail("Exception not expected: " + e);
            }
        };
        final int numConcurrentTests = 10;
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(numConcurrentTests);
        for (int i = 0; i < numConcurrentTests; i++) {
            scheduler.submit(r);
        }
        try {
            scheduler.awaitTermination(queuedJobs / numConcurrentTests, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            fail("Exception not expected: " + e);
        }
        assertEquals(queuedJobs, dequeueJobs.size());
    }


    @Test
    public void testActiveJobsCountConcurrent() {
        buildJobTable();
        buildIdempotentJobsTable();
        buildActiveJobsCountTable();
        userId = "user11";
        jobId = "job00";

        final int numThread = 3;
        final int jobsPerThread = 3;

        // Creates job prefix for each thread
        final ConcurrentLinkedQueue<String> set = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < numThread * jobsPerThread; i++) {
            final String jobId = String.format("T%d", i);
            set.add(jobId);
            final JobDBRecord jobRecord = buildJobRecord(jobId, userId);
            try {
                dynamoDBJobClient.insertJob(jobRecord);
            } catch (final ComputeException e) {
                fail("Exception not expected: " + e);
            }
        }

        final String queueId = buildJobRecord(jobId, userId).getUserServiceWorkerId();

        // Each thread peeks a job and marks it to scheduled,
        // at the end total number of Active job counts should be numThread * jobsPerThread
        Runnable r = () -> {
            // Assign jobId prefix for the jobs
            JobDBRecord job = dynamoDBJobClient.peekJob(queueId);
            while (job != null) {
                try {
                    dynamoDBJobClient.markJobScheduled(job.getJobId(), queueId);
                    assertEquals(QueueInfo.StatusEnum.PROCESSING, job.getQueueInfo().getStatus());
                    job = dynamoDBJobClient.removeJobFromQueue(job.getJobId(), job.getQueueInfo().getVersion());
                    assertEquals(QueueInfo.StatusEnum.REMOVED, job.getQueueInfo().getStatus());
                    assertNull(job.getQueueId());
                    job = dynamoDBJobClient.peekJob(queueId);
                } catch (final ComputeException e) {
                    fail("Exception not expected: " + e);
                }
            }
        };

        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(numThread);
        for (int i = 0; i < numThread; i++) {
            scheduler.submit(r);
        }
        try {
            scheduler.awaitTermination(jobsPerThread, TimeUnit.SECONDS);
            assertEquals(numThread * jobsPerThread, dynamoDBJobClient.getJobCount().longValue());
            assertEquals(numThread * jobsPerThread, dynamoDBActiveJobsCountClient.getCount(queueId));
            assertEquals(1L, dynamoDBActiveJobsCountClient.getItemCount().longValue());
        } catch (final InterruptedException | ComputeException e) {
            fail("Exception not expected: " + e);
        }

        for (int i = 0; i < numThread; i++) {
            set.add(String.format("T%d", i));
        }

        // Each thread marks different jobs in a random terminal state,
        // at the end total number of Active job counts should be 0
        r = () -> {
            final Random rand = new Random();
            for (int i = 1; i <= jobsPerThread; i++) {
                final String jobId = set.remove();
                try {
                    final JobDBRecord jobRecord = dynamoDBJobClient.getJob(jobId);
                    switch (rand.nextInt(2)) {
                        case 0:
                            dynamoDBJobClient.markJobFailed(jobId, null, null, null, jobRecord.getUserServiceWorkerId());
                            break;
                        case 1:
                            dynamoDBJobClient.markJobCanceled(jobId, null, jobRecord.getUserServiceWorkerId());
                            break;
                        default:
                            dynamoDBJobClient.markJobCompleted(jobId, null, null, jobRecord.getUserServiceWorkerId());
                    }
                } catch (final ComputeException e) {
                    fail("Exception not expected: " + e);
                }
            }
        };
        for (int i = 0; i < numThread; i++) {
            scheduler.submit(r);
        }
        try {
            scheduler.awaitTermination(jobsPerThread, TimeUnit.SECONDS);
            final String userServiceWorkerId = buildJobRecord(jobId, userId).getUserServiceWorkerId();
            assertEquals(numThread * jobsPerThread, dynamoDBJobClient.getJobCount().longValue());
            assertEquals(0, dynamoDBActiveJobsCountClient.getCount(userServiceWorkerId));
            assertEquals(1L, dynamoDBActiveJobsCountClient.getItemCount().longValue());
        } catch (final InterruptedException | ComputeException e) {
            fail("Exception not expected: " + e);
        }
    }

    @Test
    public void testInsertBatchWithRetry() throws ComputeException {
        buildJobTable();
        // Try DynamoDB
        final DynamoDB dynamoDB = Mockito.mock(DynamoDB.class);
        final DynamoDBMapper dynamoDBMapper = Mockito.mock(DynamoDBMapper.class);
        spy(dynamoDB);

        final int arrayJobSize = 10_000;
        final String arrayJobId = JobDBRecord.newArrayJobId();

        final List<JobDBRecord> records = new ArrayList<>();
        // insert array jobs (share same uuid prefix)
        for (int i = 0; i < arrayJobSize; i++) {
            final JobDBRecord r = buildJobRecord();
            jobId = JobDBRecord.createChildArrayJobId(arrayJobId, i);
            r.setJobId(jobId);
            records.add(r);
        }

        Mockito.doReturn(dynamoDB).when(dynamoDBJobClient).getDynamoDB();
        Mockito.doReturn(dynamoDBMapper).when(dynamoDBJobClient).getProgressTableDbMapper();

        // Generate some failed batch write
        final FailedBatch failedBatch = new FailedBatch();
        final Map<String, List<WriteRequest>> unprocessed = new HashMap<>();
        final WriteRequest writeRequest = new WriteRequest().withPutRequest(new PutRequest());
        unprocessed.put("write", Arrays.asList(writeRequest));
        failedBatch.setUnprocessedItems(unprocessed);
        final List<FailedBatch> failedBatches = new ArrayList<>();
        failedBatches.add(failedBatch);

        Mockito.doReturn(failedBatches).when(dynamoDBMapper).batchSave(any(List.class));

        // The retry calls this function to write unfinished writes.
        Mockito.doReturn(new BatchWriteItemOutcome(new BatchWriteItemResult())).when(dynamoDB).batchWriteItemUnprocessed(any(Map.class));

        dynamoDBJobClient.insertJobs(records);

        verify(dynamoDBMapper, times(1)).batchSave(any(List.class));
        verify(dynamoDB, times(1)).batchWriteItemUnprocessed(any(Map.class));

    }


    public void buildArrayJobsTable() throws ComputeException {
        buildJobTable();
        final int arrayJobSize = 100;
        final String arrayJobId = JobDBRecord.newArrayJobId();

        final List<JobDBRecord> records = new ArrayList<>();
        // insert array jobs (share same uuid prefix)
        for (int i = 0; i < arrayJobSize; i++) {
            final JobDBRecord r = buildJobRecord();
            jobId = JobDBRecord.createChildArrayJobId(arrayJobId, i);
            r.setJobId(jobId);
            records.add(r);
        }

        dynamoDBJobClient.insertJobs(records);

        String nextToken = null;
        int returnJobs = 0;
        do {
            final SearchJobsResult result = dynamoDBJobClient.getArrayJobs(arrayJobId, nextToken);
            assertNotNull(result);
            for (final JobDBRecord job : result.getJobs()) {
                assertTrue(job.getJobId().startsWith(arrayJobId + ":"));
            }
            returnJobs += result.getJobs().size();
            nextToken = result.getNextToken();
        } while (!StringUtil.isNullOrEmpty(nextToken));

        assertEquals(arrayJobSize, returnJobs);
    }


    @Test
    public void testGetJobsByPrefixJobNotArray() throws ComputeException {
        buildArrayJobsTable();

        // Add a regular job to see if it's not retrieved by getArrayJobs
        final JobDBRecord record = buildJobRecord();
        record.setJobId("A_REGULAR_JOB");
        dynamoDBJobClient.insertJob(record);
        final SearchJobsResult result = dynamoDBJobClient.getArrayJobs("A_REGULAR_JOB", null);
        assertEquals(0, result.getJobs().size());
    }

    @Test
    public void testGetJobsByPrefixJobNotPresent() throws ComputeException {
        buildArrayJobsTable();
        final SearchJobsResult result = dynamoDBJobClient.getArrayJobs("abc", null);
        assertEquals(0, result.getJobs().size());
    }


    @Test
    public void testGetJobsByPrefixJobInvalidToken() throws ComputeException {
        buildArrayJobsTable();
        // Gets an exception on invalid token
        validateThrows(ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.BAD_REQUEST),
                () -> dynamoDBJobClient.getArrayJobs(JobDBRecord.newArrayJobId(), "gibberish"));
    }


    @Test
    public void testSetBatchJobId() throws ComputeException {
        buildJobTable();
        final String batchJobId = "batch-job-id";
        final String jobId = JobDBRecord.newJobId();
        final JobDBRecord jobRecord = buildJobRecord(jobId, "user");
        jobRecord.setBatch(true);
        dynamoDBJobClient.insertJob(jobRecord);
        dynamoDBJobClient.setBatchJobId(jobId, batchJobId);
        final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
        assertEquals(batchJobId, job.getSpawnedBatchJobId());
    }

    @Test
    public void testIsBatchJob() throws ComputeException {
        buildJobTable();
        final String batchJobId = "batch-job-id";
        final String jobId = JobDBRecord.newJobId();
        final JobDBRecord jobRecord = buildJobRecord(jobId, "user");
        jobRecord.setBatch(true);
        dynamoDBJobClient.insertJob(jobRecord);
        dynamoDBJobClient.setBatchJobId(jobId, batchJobId);
        final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
        assertEquals(dynamoDBJobClient.isBatchJob(jobId), true);
    }

    @Test
    public void testAddStepExecutionArn() throws ComputeException {
        buildJobTable();
        final String stepArn = "some-arn";
        final String jobId = JobDBRecord.newJobId();
        final JobDBRecord jobRecord = buildJobRecord(jobId, "user");
        dynamoDBJobClient.insertJob(jobRecord);
        dynamoDBJobClient.addStepExecutionArn(jobId, stepArn);
        final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
        assertNotNull(job.getStepExecutionArns().size());
        assertEquals(1, job.getStepExecutionArns().size());
        assertEquals(stepArn, job.getStepExecutionArns().get(0));
    }

    @Test
    public void testThrottleDuringUpdateItem() throws ComputeException {
        buildJobTable();
        final UpdateItemSpec updateItemSpec = new UpdateItemSpec();
        final String stepArn = "some-arn";
        final String jobId = JobDBRecord.newJobId();
        final JobDBRecord jobRecord = buildJobRecord(jobId, "user");
        dynamoDBJobClient.insertJob(jobRecord);
        dynamoDBJobClient.addStepExecutionArn(jobId, stepArn);
        Mockito.doThrow(new AmazonClientException(ThrottleCheck.THROTTLING_STRINGS[1])).when(dynamoDBJobClient).updateWithConditionCheckDescription(any(Table.class), any(UpdateItemSpec.class), anyString());
        validateThrows(ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.TOO_MANY_REQUESTS),
                () -> dynamoDBJobClient.updateItemSpec(computeConfig.getDbProgressTable(), updateItemSpec, jobId, "Exception expected"));
    }

    @Test
    public void testExceptionCoverageDuringUpdateItem() throws ComputeException {
        buildJobTable();
        final UpdateItemSpec updateItemSpec = new UpdateItemSpec();
        final String stepArn = "some-arn";
        final String jobId = JobDBRecord.newJobId();
        final JobDBRecord jobRecord = buildJobRecord(jobId, "user");
        dynamoDBJobClient.insertJob(jobRecord);
        dynamoDBJobClient.addStepExecutionArn(jobId, stepArn);
        Mockito.doThrow(new NotFoundException("Ambiguous not found exception")).when(dynamoDBJobClient).updateWithConditionCheckDescription(any(Table.class), any(UpdateItemSpec.class), anyString());
        validateThrows(ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVER_UNEXPECTED),
                () -> dynamoDBJobClient.updateItemSpec(computeConfig.getDbProgressTable(), updateItemSpec, jobId, "Exception expected"));
    }


}
