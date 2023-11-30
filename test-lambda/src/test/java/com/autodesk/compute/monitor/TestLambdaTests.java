package com.autodesk.compute.monitor;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.BatchResultErrorEntry;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.OxygenClient;
import com.autodesk.compute.common.model.Job;
import com.autodesk.compute.common.model.JobArgs;
import com.autodesk.compute.common.model.SearchResult;
import com.autodesk.compute.common.model.Status;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.HealthReport;
import com.autodesk.compute.model.JobType;
import com.autodesk.compute.model.RunJobResult;
import com.autodesk.compute.model.SlackMessage;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.testlambda.SNSClient;
import com.autodesk.compute.testlambda.TestLambdaProcessor;
import com.autodesk.compute.testlambda.gen.ApiException;
import com.autodesk.compute.testlambda.gen.DevelopersApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import io.vavr.control.Either;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.autodesk.compute.monitor.TestUtils.*;
import static com.autodesk.compute.test.TestHelper.makeAnswer;
import static com.autodesk.compute.test.TestHelper.makeNullAnswer;
import static org.mockito.Mockito.*;

@Slf4j
@Category(UnitTests.class)
public class TestLambdaTests {

    private final MockWebServer mockWebServer;

    @Mock
    private OxygenClient oxygenMock;

    @InjectMocks
    @Spy
    private final TestLambdaProcessor processor = new TestLambdaProcessor();

    @InjectMocks
    @Spy
    private SNSClient sns;

    @Mock
    private AmazonSQS sqs;

    @Mock
    private DevelopersApi jm;

    @Mock
    private AmazonS3 s3;

    @SneakyThrows({IOException.class, ComputeException.class})
    public TestLambdaTests() {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        MockitoAnnotations.openMocks(this);
        when(processor.makeS3Client()).thenReturn(s3);
        when(processor.getSlackAttachments()).thenCallRealMethod();
        when(oxygenMock.getNewCloudOSComputeToken()).thenReturn("a-token");
    }

    @Test
    public void testSQSDeleteWithNoMessages() {
        when(sns.makeSQSClient()).thenReturn(sqs);
        final Pair<Integer, List<BatchResultErrorEntry>> deletedMessages = sns.deleteSqsMessages(Collections.emptyList(), Collections.emptyList());
        Assert.assertEquals("No messages should have been deleted", 0, (int)deletedMessages.getLeft());
    }

    @Test
    public void testValidateJobsEmpty() {
        final ArrayList<CompletableFuture<RunJobResult>> submittedJobs = new ArrayList<>();
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        processor.verifyJobs(
                Collections.emptyList(),
                JobType.ECS,
                Collections.emptyList(),
                submittedJobs);
        Assert.assertEquals(JobType.ECS.getJobCount(), submittedJobs.size());
        Assert.assertEquals("One message should have been sent", 1, processor.getSlackAttachments().size());
    }

    @Test
    public void makeJobManagerClient() throws ApiException {
        final String url = mockWebServer.url("/").toString();
        when(processor.getBasePath()).thenReturn(url);
        final DevelopersApi api = processor.makeJobManagerClient("OxygenToken");
        mockWebServer.enqueue(new MockResponse().setResponseCode(502));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody("{ }"));
        api.getJob("42", null);
        Assert.assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test(expected=AmazonServiceException.class)
    public void makeS3UploadThrow() throws IOException {
        when(s3.putObject(anyString(), anyString(), any(InputStream.class), any(ObjectMetadata.class)))
                .thenThrow(new AmazonServiceException("Test exception") );
        final HealthReport healthReport = HealthReport.builder().build();
        processor.sendHealthReportToS3(healthReport);
    }

    @SneakyThrows(JsonProcessingException.class)
    private JobArgs makeMockJobArgs() {
        final JobArgs jobArgs = new JobArgs();
        jobArgs.setService("fpccomp-c-uw2-sb");
        jobArgs.setWorker("mock-worker");
        jobArgs.setPayload(Json.mapper.readTree("{ \"hello\": "
                + counter.incrementAndGet() + " }"));
        return jobArgs;
    }

    @Test
    public void emptyMocksMainHandler() throws ComputeException {
        when(oxygenMock.getNewCloudOSComputeToken()).thenReturn("A-Fake-Token");
        when(processor.searchRecentJobs(anyLong())).thenReturn(Collections.emptyList());
        when(processor.searchRecentJobsGroupingByWorker()).thenReturn(Collections.emptyMap());
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        when(processor.runAsync(any(Supplier.class))).thenReturn(doneImmediately());
        // Since searchRecentJobsGroupingByWorker takes no arguments, it gets called when mocked and this screws with the count
        // The fix is to clear the invocations counter before calling process
        clearInvocations(processor);
        processor.process();
        verify(processor, times(1)).searchRecentJobsGroupingByWorker();
    }

    @Test
    public void testSearchRecentJobsWithException() throws ApiException
    {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        // The handler.searchRecentJobs method will call jm.searchRecentJobs twice.
        // The first time we return two jobs, and include a next token, so that
        // it will repeat the call.
        // For the purposes of this test, the second time throws an exception.
        final Answer<SearchResult> answers = makeAnswer(
                Either.right(makeSearchResult(2, null, true)),
                Either.left(new ApiException("Text exception")));
        when(jm.searchRecentJobs(anyString(), any(Integer.class), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(answers);
        final List<Job> recentJobs = processor.searchRecentJobs(10);

        // The exception causes a message to be put into the collection
        Assert.assertEquals(1, processor.getSlackAttachments().size());
        // But the results from the first page are reported back
        Assert.assertEquals(2, recentJobs.size());
    }

    private SearchResult makeSearchResult(final int jobsToReturn, final String worker, final boolean hasNextPage) {
        final SearchResult searchResult = new SearchResult();
        if (hasNextPage)
            searchResult.setNextToken(ComputeStringOps.makeNextToken("next-token"));
        searchResult.setLastUpdateTime(Long.toString(System.currentTimeMillis()));
        final List<Job> jobs = IntStream.range(0, jobsToReturn).mapToObj(n -> makeMockJob(worker)).collect(Collectors.toList());
        searchResult.setJobs(jobs);
        return searchResult;
    }

    @Test
    public void testSearchRecentJobsMultiplePages() throws ApiException
    {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final Answer<SearchResult> answers = makeAnswer(
                Either.right(makeSearchResult(3, "worker1", true)),
                Either.right(makeSearchResult(4, "worker2", false)));
        when(jm.searchRecentJobs(anyString(), any(Integer.class), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(answers);
        final List<Job> recentJobs = processor.searchRecentJobs(10);
        Assert.assertEquals(7, recentJobs.size());
    }

    @Test
    public void testSearchRecentJobsGroupingByWorker() throws ApiException
    {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final Answer<SearchResult> answers = makeAnswer(
                Either.right(makeSearchResult(3, "worker1", true)),
                Either.right(makeSearchResult(4, "worker2", false)));
        when(jm.searchRecentJobs(anyString(), any(Integer.class), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(answers);
        final Map<String, List<Job>> recentJobs = processor.searchRecentJobsGroupingByWorker();
        Assert.assertEquals(2, recentJobs.size());
        Assert.assertEquals(3, recentJobs.get("worker1").size());
        Assert.assertEquals(4, recentJobs.get("worker2").size());
    }

    @Test
    public void testSearchJobsEmptyRecentAndSearch() throws ApiException {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final Answer<SearchResult> answers = makeAnswer(Either.left(new ApiException("Test exception")));
        when(jm.searchRecentJobs(anyString(), any(Integer.class), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(answers);
        when(processor.getSubmittedJobs()).thenReturn(Collections.emptyList());
        final HealthReport.TestResult result = processor.testSubmittedJobsAppearInSearch();
        Assert.assertEquals(HealthReport.TestResult.FAILED, result);
        // Two messages are returned: one from the exception thrown in search,
        // and one from the lack of results.
        Assert.assertEquals(2, processor.getSlackAttachments().size());
    }

    // In this test, we didn't submit any new jobs, so when we search later
    // on, we expect to get the happy message that we matched everything
    // we were looking for; namely nothing.
    @Test
    public void testSearchJobsNoRecentJobsExpected() throws ApiException {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final SearchResult worker1Jobs = makeSearchResult(3, "worker1", false);
        final Answer<SearchResult> answers = makeAnswer(Either.right(worker1Jobs));
        when(jm.searchRecentJobs(anyString(), any(Integer.class), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(answers);
        when(processor.getSubmittedJobs()).thenReturn(Collections.emptyList());
        final HealthReport.TestResult result = processor.testSubmittedJobsAppearInSearch();
        // Search succeeded; no jobs found.
        Assert.assertEquals(HealthReport.TestResult.SUCCESS, result);
        Assert.assertEquals(0, processor.getSlackAttachments().size());
    }

    // In this test, we look for one recent job, and we find it.
    @Test
    public void testSearchJobsRecentSomeFound() throws ApiException
    {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final SearchResult worker1Jobs = makeSearchResult(3, "worker1", false);
        final Answer<SearchResult> answers = makeAnswer(Either.right(worker1Jobs));
        when(jm.searchRecentJobs(anyString(), any(Integer.class), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(answers);
        // Return one job
        when(processor.getSubmittedJobs()).thenReturn(Lists.newArrayList(
                worker1Jobs.getJobs().stream().limit(1).map(Job::getJobID).collect(Collectors.toList())));
        final HealthReport.TestResult result = processor.testSubmittedJobsAppearInSearch();
        // Search succeeded; one job found.
        Assert.assertEquals(1, processor.getFoundJobs().size());
        Assert.assertEquals(HealthReport.TestResult.SUCCESS, result);
        Assert.assertEquals(0, processor.getSlackAttachments().size());
    }

    // In this test, we look for recent jobs, but don't find any of them.
    // That's an error.
    @Test
    public void testSearchJobsRecentNoneFound() throws ApiException {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final SearchResult worker1Jobs = makeSearchResult(3, "worker1", false);
        final Answer<SearchResult> answers = makeAnswer(Either.right(worker1Jobs));
        when(jm.searchRecentJobs(anyString(), any(Integer.class), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(answers);
        // Return one job
        when(processor.getSubmittedJobs()).thenReturn(Lists.newArrayList("job-not-found"));
        final HealthReport.TestResult result = processor.testSubmittedJobsAppearInSearch();
        // Search failed; the submitted job was not found.
        Assert.assertEquals(HealthReport.TestResult.FAILED, result);
        Assert.assertEquals(0, processor.getFoundJobs().size());
        // One message because of the failure.
        Assert.assertEquals(1, processor.getSlackAttachments().size());
    }

    // In this test, we search for several recent jobs,
    // and find them all.
    @Test
    public void testSearchJobsRecentAllFound() throws ApiException {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final SearchResult worker1Jobs = makeSearchResult(3, "worker1", false);
        final List<String> worker1JobIds = worker1Jobs.getJobs().stream().map(Job::getJobID).collect(Collectors.toList());
        final Answer<SearchResult> answers = makeAnswer(Either.right(worker1Jobs));
        when(jm.searchRecentJobs(anyString(), any(Integer.class), anyString(), anyString(), anyString(), nullable(String.class)))
                .thenAnswer(answers);
        // Return one job
        when(processor.getSubmittedJobs()).thenReturn(worker1JobIds);
        final HealthReport.TestResult result = processor.testSubmittedJobsAppearInSearch();
        // Search succeeded; all jobs found
        Assert.assertEquals(HealthReport.TestResult.SUCCESS, result);
        Assert.assertEquals(3, processor.getFoundJobs().size());
        // No messages because of the success.
        Assert.assertEquals(0, processor.getSlackAttachments().size());
    }

    @Test
    public void testCancelJob() throws ApiException {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        doAnswer(makeNullAnswer(
                Either.right(1),
                Either.left(new ApiException("thrown exception"))))
                .when(jm).deleteJob(anyString());
        processor.cancelJob("job1", processor.getSlackAttachments(), SlackMessage.MessageSeverity.WARNING);
        Assert.assertEquals(1, processor.getSlackAttachments().size());
        Assert.assertTrue(processor.getSlackAttachments().get(0).getTitle().contains("Canceled job"));
        // This one throws
        processor.cancelJob("job2", processor.getSlackAttachments(), SlackMessage.MessageSeverity.DANGER);
        Assert.assertEquals(2, processor.getSlackAttachments().size());
        Assert.assertTrue(processor.getSlackAttachments().get(1).getTitle().contains("Failed to cancel"));
    }

    @Test
    public void testPostJob() throws ApiException
    {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final Answer<Job> jobs = makeAnswer(
                Either.right(makeMockJob("worker")),
                Either.left(new ApiException("test exception")));
        when(jm.createJob(any(JobArgs.class), any(Boolean.class))).thenAnswer(jobs);
        Job createdJob = processor.postJob(makeMockJobArgs(), JobType.BATCH);
        Assert.assertNotNull(createdJob);
        Assert.assertEquals(0, processor.getSlackAttachments().size());
        // This one throws
        createdJob = processor.postJob(makeMockJobArgs(), JobType.ECS);
        Assert.assertNull(createdJob);
        Assert.assertEquals(1, processor.getSlackAttachments().size());
        Assert.assertTrue(processor.getSlackAttachments().get(0).getTitle().contains("failed to submit"));
    }

    @Test
    public void submitNewJobNoWorker() {
        final ArrayList<SlackMessage.Attachment> messages = new ArrayList<>();
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        when(processor.getWorkerForJobType(any(JobType.class))).thenReturn(null);
        final RunJobResult result = processor.submitNewJob(JobType.ECS);
        Assert.assertNull(result.getJob());
        Assert.assertEquals(HealthReport.TestResult.NOT_CONFIGURED_HERE, result.getTestResult());
    }

    @Test
    public void submitNewJobWithWorker() throws ApiException {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        when(processor.getWorkerForJobType(any(JobType.class))).thenAnswer(
                makeAnswer(Either.right("ecs-worker"), Either.right("batch-worker"), Either.right("gpu-worker")));
        when(jm.createJob(any(JobArgs.class), any(Boolean.class))).thenAnswer(
                makeAnswer(Either.right(makeMockJob("ecs-worker")),
                        Either.right(makeMockJob("batch-worker")),
                        Either.left(new ApiException("submit failed")))
        );
        final RunJobResult result1 = processor.submitNewJob(JobType.ECS);
        Assert.assertNotNull(result1.getJob());
        Assert.assertEquals(0, processor.getSlackAttachments().size());
        Assert.assertEquals(HealthReport.TestResult.SUCCESS, result1.getTestResult());
        final RunJobResult result2 = processor.submitNewJob(JobType.BATCH);
        Assert.assertNotNull(result2.getJob());
        Assert.assertEquals(0, processor.getSlackAttachments().size());
        Assert.assertEquals(HealthReport.TestResult.SUCCESS, result2.getTestResult());
        final RunJobResult result3 = processor.submitNewJob(JobType.BATCH);
        Assert.assertNull(result3.getJob());
        Assert.assertEquals(1, processor.getSlackAttachments().size());
        Assert.assertEquals(HealthReport.TestResult.FAILED_TO_SUBMIT, result3.getTestResult());
        Assert.assertEquals(2, processor.getSubmittedJobs().size());
    }

    @Test
    public void findQueuedJobsNotEnoughJobs() {
        final HealthReport.TestResult result = processor.findQueuedJobStatus(Collections.emptyList(), JobType.ECS);
        Assert.assertEquals(HealthReport.TestResult.NOT_CONFIGURED_HERE, result);
    }

    @Test
    public void findQueuedJobsNoQueuedJobs() {
        final ArrayList<RunJobResult> runningJobs = Lists.newArrayList(
                new RunJobResult(HealthReport.TestResult.SUCCESS, makeMockJob("job1")),
                new RunJobResult(HealthReport.TestResult.SUCCESS, makeMockJob("job1")),
                new RunJobResult(HealthReport.TestResult.FAILED, null),
                new RunJobResult(HealthReport.TestResult.SUCCESS, makeMockJob("job2"))
        );
        final HealthReport.TestResult result = processor.findQueuedJobStatus(
                runningJobs, JobType.ECS);
        Assert.assertEquals(HealthReport.TestResult.FAILED, result);
        Assert.assertEquals(1, processor.getSlackAttachments().size());
    }

    @Test
    public void findQueuedJobsHappyPath() {
        final ArrayList<RunJobResult> runningJobs = Lists.newArrayList(
                new RunJobResult(HealthReport.TestResult.SUCCESS, makeMockJob("job1", Status.QUEUED)),
                new RunJobResult(HealthReport.TestResult.SUCCESS, makeMockJob("job1", Status.COMPLETED, true)),
                new RunJobResult(HealthReport.TestResult.FAILED, null),
                new RunJobResult(HealthReport.TestResult.SUCCESS, makeMockJob("job2", Status.INPROGRESS, true))
        );
        final HealthReport.TestResult result = processor.findQueuedJobStatus(
                runningJobs, JobType.ECS);
        Assert.assertEquals(HealthReport.TestResult.SUCCESS, result);
        Assert.assertEquals(0, processor.getSlackAttachments().size());
    }

    @Test
    public void validateJobsAllJobsCompleted() {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final ArrayList<RunJobResult> jobResults = new ArrayList<>();
        final ArrayList<CompletableFuture<RunJobResult>> futures = new ArrayList<>();
        final String workerForECS = JobType.ECS.getWorker();
        final List<Job> recentJobs = Lists.newArrayList(
                makeMockJob(workerForECS),
                makeMockJob(workerForECS),
                makeMockJob(workerForECS));
        // Three completed jobs means that it will submit three more.
        processor.verifyJobs(recentJobs, JobType.ECS, jobResults, futures);
        Assert.assertEquals(3, futures.size());
        Assert.assertEquals(1, processor.getSlackAttachments().size());
        Assert.assertTrue(processor.getSlackAttachments().get(0).getTitle().contains("new: 3"));
        Assert.assertTrue(processor.getSlackAttachments().get(0).getTitle().contains("found existing: 0"));
    }

    @Test
    public void validateJobsAllJobsInProgress() {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final ArrayList<RunJobResult> jobResults = new ArrayList<>();
        final ArrayList<CompletableFuture<RunJobResult>> futures = new ArrayList<>();
        final String worker = JobType.BATCH.getWorker();
        final List<Job> recentJobs = Lists.newArrayList(
                makeMockJob(worker, Status.INPROGRESS, true),
                makeMockJob(worker, Status.INPROGRESS, true),
                makeMockJob(worker, Status.INPROGRESS, true));
        // Three completed jobs means that it will submit three more.
        processor.verifyJobs(recentJobs, JobType.BATCH, jobResults, futures);
        Assert.assertEquals(0, futures.size());
        Assert.assertEquals(1, processor.getSlackAttachments().size());
        Assert.assertTrue(processor.getSlackAttachments().get(0).getTitle().contains("new: 0"));
        Assert.assertTrue(processor.getSlackAttachments().get(0).getTitle().contains("found existing: 3"));
    }

    @Test
    public void validateJobsTimedOutJobsInProgress() throws ApiException {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final ArrayList<RunJobResult> jobResults = new ArrayList<>();
        final ArrayList<CompletableFuture<RunJobResult>> futures = new ArrayList<>();
        final String worker = JobType.BATCH.getWorker();
        final List<Job> recentJobs = Lists.newArrayList(
                makeMockJob(worker, Status.INPROGRESS, 300, true),
                makeMockJob(worker, Status.INPROGRESS, 300, true),
                makeMockJob(worker, Status.INPROGRESS, 300, true));
        // Three completed jobs means that it will submit three more.
        processor.verifyJobs(recentJobs, JobType.BATCH, jobResults, futures);
        Assert.assertEquals(3, futures.size());
        verify(jm, times(3)).deleteJob(anyString());
        Assert.assertTrue(processor.getSlackAttachments().get(processor.getSlackAttachments().size() - 1).getTitle().contains("new: 3"));
        Assert.assertTrue(processor.getSlackAttachments().get(processor.getSlackAttachments().size() - 1).getTitle().contains("found existing: 0"));
    }

    @Test
    public void validateJobsAllFailed() throws ApiException {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final ArrayList<RunJobResult> jobResults = new ArrayList<>();
        final ArrayList<CompletableFuture<RunJobResult>> futures = new ArrayList<>();
        final String worker = JobType.BATCH.getWorker();
        final List<Job> recentJobs = Lists.newArrayList(
                makeMockJob(worker, Status.FAILED, 15, true),
                makeMockJob(worker, Status.FAILED, 15, true),
                makeMockJob(worker, Status.FAILED, 15, true));
        // Three completed jobs means that it will submit three more.
        processor.verifyJobs(recentJobs, JobType.BATCH, jobResults, futures);
        Assert.assertEquals(3, futures.size());
        verify(jm, never()).deleteJob(anyString());
        Assert.assertTrue(processor.getSlackAttachments().get(processor.getSlackAttachments().size() - 1).getTitle().contains("new: 3"));
        Assert.assertTrue(processor.getSlackAttachments().get(processor.getSlackAttachments().size() - 1).getTitle().contains("found existing: 0"));
    }

    @Test
    public void validateJobsTooManyJobsInProgress() throws ApiException {
        when(processor.makeJobManagerClient(anyString())).thenReturn(jm);
        final ArrayList<RunJobResult> jobResults = new ArrayList<>();
        final ArrayList<CompletableFuture<RunJobResult>> futures = new ArrayList<>();
        final String worker = JobType.BATCH.getWorker();
        final List<Job> recentJobs = Lists.newArrayList(
                makeMockJob(worker, Status.INPROGRESS, 120, true),
                makeMockJob(worker, Status.INPROGRESS, 120, true),
                makeMockJob(worker, Status.INPROGRESS, 120, true),
                makeMockJob(worker, Status.INPROGRESS, 120, true),
                makeMockJob(worker, Status.INPROGRESS, 120, true),
                makeMockJob(worker, Status.INPROGRESS, 120, true)
        );
        // Three completed jobs means that it will submit three more.
        processor.verifyJobs(recentJobs, JobType.BATCH, jobResults, futures);
        Assert.assertEquals(0, futures.size());
        verify(jm, times(3)).deleteJob(anyString());
        Assert.assertTrue(processor.getSlackAttachments().get(processor.getSlackAttachments().size() - 1).getTitle().contains("new: 0"));
        Assert.assertTrue(processor.getSlackAttachments().get(processor.getSlackAttachments().size() - 1).getTitle().contains("found existing: 3"));
    }
}


