package com.autodesk.compute.dynamodb;

import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.util.JobCacheLoader;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Category(UnitTests.class)
@Slf4j
public class TestJobCache {

    @Mock
    private DynamoDBJobClient dynamoDBJobClient;

    private JobCacheLoader jobCacheLoader;

    private LoadingCache<String, JobDBRecord> jobCache;

    private JobDBRecord makeJobRecord(final String jobId) {
        final JobDBRecord job = JobDBRecord.createDefault("job0");
        job.setService("testService");
        job.setWorker("testworker");
        job.setBatch(false);
        return job;
    }

    private JobDBRecord testJobRecord;

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
        jobCacheLoader = Mockito.spy(new JobCacheLoader(dynamoDBJobClient));
        jobCache = Mockito.spy(CacheBuilder.newBuilder()
                .expireAfterAccess(300, TimeUnit.SECONDS)
                .refreshAfterWrite(10, TimeUnit.SECONDS)
                .build(jobCacheLoader));
        testJobRecord = makeJobRecord("job0");
    }

    @Test
    public void testCache() throws ComputeException, ExecutionException {
        Mockito.doReturn(testJobRecord).when(dynamoDBJobClient).getJob("job0");
        final JobDBRecord jobRecord = jobCache.get("job0");
        final JobDBRecord jobRecordCopy = jobCache.get("job0");
        // ensure the loader/db is only called once for two gets
        verify(jobCacheLoader, times(1)).load(anyString());
        verify(dynamoDBJobClient, times(1)).getJob(anyString());
    }

    @Test
    public void testRefresh() throws ComputeException, ExecutionException {
        Mockito.doReturn(testJobRecord).when(dynamoDBJobClient).getJob("job0");
        final JobDBRecord jobRecord = jobCache.get("job0");
        jobCache.refresh("job0");
        final JobDBRecord jobRecordCopy = jobCache.get("job0");
        // ensure the loader/db is called twice for two gets w refresh between
        verify(jobCacheLoader, times(2)).load(anyString());
        verify(dynamoDBJobClient, times(2)).getJob(anyString());
    }

    @Test
    public void testNoReloadTerminal() throws ComputeException, ExecutionException {
        testJobRecord.setStatus(JobStatus.COMPLETED.toString());
        Mockito.doReturn(testJobRecord).when(dynamoDBJobClient).getJob("job0");
        final JobDBRecord jobRecord = jobCache.get("job0");
        jobCache.refresh("job0");
        final JobDBRecord jobRecordCopy = jobCache.get("job0");
        // ensure the loader/db is only called once for two gets w refresh, due to terminal state
        verify(jobCacheLoader, times(1)).load(anyString());
        verify(dynamoDBJobClient, times(1)).getJob(anyString());
    }

    @Test
    public void testJobNotFound() throws ComputeException, ExecutionException {
        Mockito.doReturn(null).when(dynamoDBJobClient).getJob(anyString());
        validateThrows(
                ExecutionException.class,
                new Matchers.MessageMatcher("NOT_FOUND"),
                () -> jobCache.get("job0"));
    }
}