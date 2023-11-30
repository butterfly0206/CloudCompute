package com.autodesk.compute.jobmanager;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.util.StringUtils;
import com.autodesk.compute.auth.oauth.OxygenSecurityContext;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.OAuthTokenUtils;
import com.autodesk.compute.common.model.Job;
import com.autodesk.compute.common.model.SearchResult;
import com.autodesk.compute.common.model.Status;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.jobmanager.api.impl.SearchApiServiceImpl;
import com.autodesk.compute.jobmanager.util.JobManagerException;
import com.autodesk.compute.model.OAuthToken;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.categories.UnitTests;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.*;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.configuration.ComputeConstants.DynamoFields.*;
import static org.junit.Assert.*;


@Category(UnitTests.class)
@Slf4j
public class TestSearchApi {

    private static DynamoDBJobClient dynamoDBJobClient;
    private static AmazonDynamoDB amazonDynamoDBClient;
    private static DynamoDB dynamoDB;
    private static ComputeConfig computeConfig;
    private static ArrayList<String> tags;
    private static Table jobTable;
    private static final String jobId = "jobId";
    private static final String userId = "test_userid";
    private static String serviceClientId = null;
    private static OAuthToken oauthToken = null;
    private static MultivaluedMap<String, String> requestHeader;
    private static OxygenSecurityContext oxygenContext;
    private SearchApiServiceImpl searchApiService;

    @ClassRule
    public static final LocalDynamoDBCreationRule localDbCreator = new LocalDynamoDBCreationRule();

    @BeforeClass
    public static void beforeClass() {
        // set config values
        tags = new ArrayList<>();
        tags.add("test");
        tags.add("completion");

        // build the DB
        amazonDynamoDBClient = localDbCreator.getAmazonDynamoDB();
        assertNotNull(amazonDynamoDBClient);
        dynamoDBJobClient = new DynamoDBJobClient(amazonDynamoDBClient);
        assertNotNull(dynamoDBJobClient);
        dynamoDB = dynamoDBJobClient.getDynamoDB();
        assertNotNull(dynamoDB);
        computeConfig = ComputeConfig.getInstance();
        assertNotNull(computeConfig);
        requestHeader = initRequestHeaders();
        assertNotNull(requestHeader);

        try {
            log.info("Attempting to parse oauthToken from " + requestHeader);
            oauthToken = OAuthTokenUtils.from(requestHeader);
            log.info("Parsed oauthToken " + oauthToken.toString());
            assertNotNull(oauthToken);
            assertNotNull(oauthToken.getBearerToken());
            assertFalse(oauthToken.getBearerToken().isEmpty());
            assertNotNull(oauthToken.getClientId());
            assertFalse(oauthToken.getClientId().isEmpty());
        } catch (final Exception e) {
            log.error("Unexpected exception parsing test OAuthToken", e);
            fail();
        }

        oxygenContext = new OxygenSecurityContext(ImmutableSet.of("testGatewaySecretV1", "testGatewaySecretV2"
), true, oauthToken);

    }

    private static MultivaluedHashMap<String, String> initRequestHeaders() {
        final MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();
        result.put("x-ads-token-data", Arrays.asList("{\"scope\":\"read\",\"expires_in\":3600,\"client_id\":\"test_client\",\"access_token\": {\"client_id\":\"test_client\",\"userid\":\"test_userid\",\"username\":\"test_username\",\"email\":\"test_email\",\"lastname\":\"test_lastname\",\"firstname\":\"test_firstname\",\"entitlements\":\"\"}}"));
        result.put("x-ads-appid", Arrays.asList("testAppId"));
        result.put("Authorization", Arrays.asList("Bearer someOxygenToken"));
        result.put("x-ads-gateway-secret", Arrays.asList("testGatewaySecret"));
        return result;
    }

    private String getServiceClientId() {
        if (serviceClientId == null) {
            serviceClientId = ComputeStringOps.makeServiceClientId(userId, oauthToken.getClientId(), computeConfig.getAppMoniker());
        }
        return serviceClientId;
    }

    private String getServiceClientId(final String service) {
        if (serviceClientId == null) {
            serviceClientId = ComputeStringOps.makeServiceClientId(userId, oauthToken.getClientId(), service);
        }
        return serviceClientId;
    }

    private JobDBRecord buildJobRecord() {
        // build a job record
        final JobDBRecord jobRecord = JobDBRecord.createDefault(jobId);
        jobRecord.setTags(tags);
        jobRecord.setPercentComplete(0);
        jobRecord.setOxygenToken(oauthToken.toString());
        jobRecord.setService(computeConfig.getAppMoniker());
        jobRecord.setServiceClientId(getServiceClientId());
        jobRecord.setStatus(JobStatus.SCHEDULED.toString());
        return jobRecord;
    }

    private JobDBRecord buildJobRecord(final String jobId, final String service) {
        // build a job record
        final JobDBRecord jobRecord = JobDBRecord.createDefault(jobId);
        jobRecord.setTags(tags);
        jobRecord.setPercentComplete(0);
        jobRecord.setOxygenToken(oauthToken.toString());
        jobRecord.setService(service);
        jobRecord.setServiceClientId(getServiceClientId());
        jobRecord.setStatus(JobStatus.SCHEDULED.toString());
        return jobRecord;
    }

    private static void buildJobTable() {
        if (jobTable == null) {
            try {
                final CreateTableRequest request = new CreateTableRequest()
                        .withTableName(computeConfig.getDbProgressTable())
                        .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
                        .withAttributeDefinitions(
                                new AttributeDefinition(
                                        JOB_ID, ScalarAttributeType.S),
                                new AttributeDefinition(
                                        SERVICE_CLIENT_ID, ScalarAttributeType.S),
                                new AttributeDefinition(
                                        MODIFICATION_TIME, ScalarAttributeType.N))
                        .withKeySchema(new KeySchemaElement(JOB_ID, KeyType.HASH))
                        .withGlobalSecondaryIndexes(
                                new GlobalSecondaryIndex().withIndexName(computeConfig.getDbProgressTableSearchIndex())
                                        .withKeySchema(new KeySchemaElement(SERVICE_CLIENT_ID, KeyType.HASH),
                                                new KeySchemaElement(MODIFICATION_TIME, KeyType.RANGE))
                                        .withProvisionedThroughput(new ProvisionedThroughput(10L, 10L))
                                        .withProjection(new Projection().withProjectionType(ProjectionType.ALL)))
                        .withSSESpecification(new SSESpecification().withEnabled(true));

                log.info("buildJobTable attempting to create Table with name={} and search index={} ", computeConfig.getDbProgressTable(), computeConfig.getDbProgressTableSearchIndex());
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

    private static void destroyJobTable() {
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

    @Test
    public void testSearchJobApi() throws ComputeException, JobManagerException {
        MockitoAnnotations.openMocks(this);
        buildJobTable();
        dynamoDBJobClient.insertJob(buildJobRecord());

        long creationTime = 0L;
        // getJob via DB client
        try {
            final JobDBRecord job = dynamoDBJobClient.getJob(jobId);
            assertNotNull(job);
            assertEquals(getServiceClientId(), job.getServiceClientId());
            creationTime = job.getModificationTime();
            log.info("dynamoDBJobClient.getJob gave us jobId={} with serviceClientId={}", jobId, job.getServiceClientId());
        } catch (final ComputeException e) {
            fail(makeString("Unexpected exception", e.getMessage()));
        }

        // getJobCount via DB client
        try {
            final long jobCount = dynamoDBJobClient.getJobCount().longValue();
            assertEquals(1L, jobCount);
            log.info("dynamoDBJobClient.getJobCount gave us {}", jobCount);
        } catch (final ComputeException e) {
            fail("Exception not expected: " + e);
        }

        // search by time via DB client - expecting to find a job
        try {
            final List<String> jobsFromWhenWeStarted = new ArrayList<>();
            final long utcTimeTo = new Date().getTime(); //now
            final Map<String, Object> results = dynamoDBJobClient.getJobsByTimeForPage(getServiceClientId(), null, 2, creationTime, utcTimeTo, Optional.empty());
            assertNotNull(results);
            log.info(makeString("response has ", results.size(), " map entries: ", results));

            // SearchResult requires Job objects, so we create them here
            final Iterator<JobDBRecord> iter = ((List<JobDBRecord>) results.get(DynamoDBJobClient.JOBS_FIELD)).iterator();
            if (iter.hasNext()) {
                while (iter.hasNext()) {
                    final JobDBRecord jobRecord = iter.next();
                    assertNotNull(jobRecord);
                    assertNotNull("No " + JOB_ID + " in item: " + jobRecord.toString() + ", response has " + Long.toString(results.size()) + " entries", jobRecord.getJobId());
                    if (jobRecord.getJobId() != null) {
                        jobsFromWhenWeStarted.add(jobRecord.getJobId());
                    }
                }
            } else {
                log.error(makeString("getJobsFromTime could not find jobs in time windows ",
                        Long.toString(creationTime), " and ", Long.toString(utcTimeTo), " (range is ", Long.toString(utcTimeTo - creationTime), "ms)"));
            }

            assertNotNull("getJobsByTime returned a null object", jobsFromWhenWeStarted);
            assertEquals("getJobsByTime returned the wrong number of jobs", 1, jobsFromWhenWeStarted.size());
            assertEquals(jobsFromWhenWeStarted.get(0), jobId);
        } catch (final Throwable e) {
            fail("Exception not expected: " + e.getMessage());
        }

        // perform the search via API
        assertNotNull(dynamoDBJobClient);
        searchApiService = new SearchApiServiceImpl(dynamoDBJobClient);
        assertNotNull(searchApiService);
        assertNotNull(oxygenContext);
        // setup the API search params
        log.info("starting search with service={}", computeConfig.getAppMoniker());
        final Response response = searchApiService.searchRecentJobs(computeConfig.getAppMoniker(), 1, null, null, null, null, oxygenContext);
        assertNotNull("response is null", response);

        // validate the API response
        assertNotNull("response has no entity object", response.getEntity());
        assertTrue("response entity is not an instance of SearchResult", response.getEntity() instanceof SearchResult);
        if (response.getEntity() != null) {
            if (response.getEntity() instanceof SearchResult) {
                final SearchResult searchResult = (SearchResult) response.getEntity();
                assertEquals(makeString("searchResult returned 0 jobs for serviceClientId=", getServiceClientId()), 1, searchResult.getJobs().size());
                if (searchResult.getJobs().size() == 1) {
                    final Job job = searchResult.getJobs().get(0);
                    assertEquals(job.getJobID(), jobId);
                }
                // with a maxResults of 1, we'll get a single page with a single job and
                // a nextToken that would (should we search with it) give us a second page
                // with an empty job set, indicating the end of the search results.
                final String nextToken = searchResult.getNextToken();
                assertFalse(StringUtils.isNullOrEmpty(nextToken));
            } else if (response.getEntity() instanceof List) {
                final List<Exception> errors = (List<Exception>) response.getEntity();
                log.error("response was a list of exceptions: " + errors.toArray().toString());
            }
        }
        destroyJobTable();
    }

    @Test
    public void testEnumAssumptions() {
        assertEquals(Status.SCHEDULED, Status.valueOf("SCHEDULED"));
        assertEquals(Status.INPROGRESS, Status.valueOf("INPROGRESS"));
        assertEquals(Status.COMPLETED, Status.valueOf("COMPLETED"));
        assertEquals(Status.CANCELED, Status.valueOf("CANCELED"));
        assertEquals(Status.FAILED, Status.valueOf("FAILED"));
    }

    @Test
    public void testToTimeAndFromTimeFormats() throws ComputeException, JobManagerException {
        MockitoAnnotations.openMocks(this);
        buildJobTable();
        dynamoDBJobClient.insertJob(buildJobRecord());
        searchApiService = new SearchApiServiceImpl(dynamoDBJobClient);

        try {
            searchApiService.searchRecentJobs(computeConfig.getAppMoniker(), 1, "notANumber", null, null, null, oxygenContext);
        } catch (final JobManagerException e) {
            assertEquals("Returned code is not 400", ComputeErrorCodes.BAD_REQUEST, e.getCode());
        }

        try {
            searchApiService.searchRecentJobs(computeConfig.getAppMoniker(), 1, null, "notANumber", null, null, oxygenContext);
        } catch (final JobManagerException e) {
            assertEquals("Returned code is not 400", ComputeErrorCodes.BAD_REQUEST, e.getCode());
        }

        searchApiService.searchRecentJobs(computeConfig.getAppMoniker(), 1, "1000", "2000", null, null, oxygenContext);
        destroyJobTable();
    }


    @Test
    public void testSearchJobApiAcrossService() throws ComputeException, JobManagerException {
        MockitoAnnotations.initMocks(this);
        buildJobTable();
        final String jobId1 = "jobId1";
        final String jobId2 = "jobId2";
        dynamoDBJobClient.insertJob(buildJobRecord(jobId1, "service1"));
        dynamoDBJobClient.insertJob(buildJobRecord(jobId2, "service2"));


        // perform the search via API
        assertNotNull(dynamoDBJobClient);
        searchApiService = new SearchApiServiceImpl(dynamoDBJobClient);
        assertNotNull(searchApiService);
        assertNotNull(oxygenContext);
        // setup the API search params
        log.info("starting search across service=");
        final Response response = searchApiService.searchRecentJobs("", 2, null, null, null, null, oxygenContext);
        assertNotNull("response is null", response);

        // validate the API response
        assertNotNull("response has no entity object", response.getEntity());
        assertTrue("response entity is not an instance of SearchResult", response.getEntity() instanceof SearchResult);

        final SearchResult searchResult = (SearchResult) response.getEntity();
        assertEquals(makeString("searchResult returned 0 jobs for serviceClientId=", getServiceClientId(null)), 2, searchResult.getJobs().size());
        final Job job1 = searchResult.getJobs().get(0);
        final Job job2 = searchResult.getJobs().get(1);
        assertTrue(new HashSet(Arrays.asList(job1.getJobID(), job2.getJobID())).equals(new HashSet(Arrays.asList(jobId1, jobId2))));

        destroyJobTable();

    }


}