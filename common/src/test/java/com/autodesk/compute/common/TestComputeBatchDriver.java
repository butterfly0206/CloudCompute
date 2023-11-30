package com.autodesk.compute.common;

import com.amazonaws.services.batch.AWSBatch;
import com.amazonaws.services.batch.model.DescribeJobsRequest;
import com.amazonaws.services.batch.model.DescribeJobsResult;
import com.amazonaws.services.batch.model.JobDetail;
import com.amazonaws.services.batch.model.TerminateJobRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestComputeBatchDriver {
    private static final String BATCH_JOB_ID = "batch-job-id";

    @Mock
    private AWSBatch batchClient;

    @Spy
    @InjectMocks
    private ComputeBatchDriver batchDriver = new ComputeBatchDriver(batchClient, ComputeBatchDriver.ForceTerminationFeature.ENABLED);

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void TestVerifyBatchJobTerminatedNoJob() {
        DescribeJobsResult describeJobsResult = new DescribeJobsResult();
        Mockito.doReturn(describeJobsResult).when(batchClient).describeJobs(any(DescribeJobsRequest.class));
        batchDriver.verifyBatchJobTerminated(BATCH_JOB_ID, "none");
        verify(batchClient, never()).terminateJob(any(TerminateJobRequest.class));
    }

    @Test
    public void TestVerifyBatchJobTerminatedRunning() {
        DescribeJobsResult describeJobsResult = new DescribeJobsResult().withJobs(
                new JobDetail().withJobId(BATCH_JOB_ID).withStatus("RUNNING"));
        Mockito.doReturn(describeJobsResult).when(batchClient).describeJobs(any(DescribeJobsRequest.class));
        batchDriver.verifyBatchJobTerminated(BATCH_JOB_ID, null);
        verify(batchClient, times(1)).terminateJob(any(TerminateJobRequest.class));
    }

    @Test
    public void TestVerifyBatchJobTerminatedInTerminal() {
        for (String status : Arrays.asList("SUCCEEDED", "FAILED")) {
            DescribeJobsResult describeJobsResult = new DescribeJobsResult().withJobs(
                    new JobDetail().withJobId(BATCH_JOB_ID).withStatus(status));
            Mockito.doReturn(describeJobsResult).when(batchClient).describeJobs(any(DescribeJobsRequest.class));
            batchDriver.verifyBatchJobTerminated(BATCH_JOB_ID, null);
        }
        verify(batchClient, never()).terminateJob(any(TerminateJobRequest.class));
    }
}
