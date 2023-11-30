package com.autodesk.compute.jobmanager;

import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.autodesk.compute.common.ComputeSqsDriver;
import com.autodesk.compute.configuration.ComputeConstants;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.discovery.ServiceResources;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.jobmanager.util.JobHelper;
import com.autodesk.compute.jobmanager.util.JobManagerException;
import com.autodesk.compute.jobmanager.util.JobScheduler;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.categories.UnitTests;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Category(UnitTests.class)
@Slf4j
public class TestJobScheduler {

    @Spy
    @InjectMocks
    private JobScheduler scheduler;

    @Mock
    private JobHelper scheduleJobHelper;

    @Mock
    private DynamoDBJobClient dynamoDBJobClient;

    @Mock
    private ComputeSqsDriver sqsDriver;

    private JobDBRecord job;

    @Before
    public void initialize() throws ComputeException {
        MockitoAnnotations.openMocks(this);

        // Create a blank message to return to prime the message loop
        final Map<String, MessageAttributeValue> attributes = new HashMap<>();
        attributes.put(ComputeConstants.DynamoFields.QUEUE_ID, new MessageAttributeValue().withStringValue("test-queue-id"));
        final Message message = new Message().withMessageAttributes(attributes);

        // One message
        when(sqsDriver.receiveOneMessage(nullable(String.class), nullable(Integer.class))).thenReturn(message).thenReturn(null);
        // One iteration
        when(scheduler.isShuttingDown()).thenReturn(false).thenReturn(true);

        // Return stock results for mocked calls
        job = new JobDBRecord();
        job.setJobId("test-job-id");
        doReturn(job).when(dynamoDBJobClient).removeJobFromQueue(any(JobDBRecord.class));
        doNothing().when(sqsDriver).deleteMessage(nullable(String.class), nullable(Message.class));
    }

    @Test
    public void testHappyPath() throws JobManagerException, ComputeException {
        doReturn(job).when(dynamoDBJobClient).peekJob(anyString());
        doNothing().when(scheduleJobHelper).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
        scheduler.run();
        verify(scheduler, times(2)).isShuttingDown();
        verify(sqsDriver, times(1)).receiveOneMessage(nullable(String.class), nullable(Integer.class));
        verify(scheduleJobHelper, times(1)).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
        verify(dynamoDBJobClient, times(1)).removeJobFromQueue(any(JobDBRecord.class));
        verify(sqsDriver, times(1)).deleteMessage(nullable(String.class), nullable(Message.class));
        scheduler.close(); //only calling this once for coverage. We're mocking around the use of it internally.
    }

    @Test
    public void testHappyNoJobPath() {
        doReturn(null).when(dynamoDBJobClient).peekJob(anyString());
        scheduler.run();
        verify(sqsDriver, times(1)).deleteMessage(nullable(String.class), nullable(Message.class));
    }

    @Test
    public void testUnhappyRemoveJobFromQueue() throws JobManagerException, ComputeException {
        doReturn(job).when(dynamoDBJobClient).peekJob(anyString());
        doNothing().when(scheduleJobHelper).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
        final ComputeException e = new ComputeException(ComputeErrorCodes.SERVER_UNEXPECTED, "message");
        doThrow(e).when(dynamoDBJobClient).removeJobFromQueue(any(JobDBRecord.class));
        scheduler.run();
        verify(scheduler, times(2)).isShuttingDown();
        verify(sqsDriver, times(1)).receiveOneMessage(nullable(String.class), nullable(Integer.class));
        verify(scheduleJobHelper, times(1)).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
    }

    @Test
    public void testUnhappyInvalidServiceName() throws JobManagerException {
        doReturn(job).when(dynamoDBJobClient).peekJob(anyString());
        final JobManagerException e = new JobManagerException(ComputeErrorCodes.INVALID_SERVICE_NAME, "message");
        doThrow(e).when(scheduleJobHelper).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
        doNothing().when(scheduleJobHelper).safelyUpdateJobStatusToFailed(any(JobDBRecord.class), anyString());
        scheduler.run();
        verify(scheduleJobHelper, times(1)).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
        verify(scheduleJobHelper, times(1)).safelyUpdateJobStatusToFailed(any(JobDBRecord.class), anyString());
    }

    @Test
    public void testUnhappyInvalidJobPayload() throws JobManagerException {
        doReturn(job).when(dynamoDBJobClient).peekJob(anyString());
        final JobManagerException e = new JobManagerException(ComputeErrorCodes.INVALID_JOB_PAYLOAD, "message");
        doThrow(e).when(scheduleJobHelper).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
        doNothing().when(scheduleJobHelper).safelyUpdateJobStatusToFailed(any(JobDBRecord.class), anyString());
        scheduler.run();
        verify(scheduleJobHelper, times(1)).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
        verify(scheduleJobHelper, times(1)).safelyUpdateJobStatusToFailed(any(JobDBRecord.class), anyString());
    }

    @Test
    public void testUnhappyTransactionConflict() throws JobManagerException {
        doReturn(job).when(dynamoDBJobClient).peekJob(anyString());
        final JobManagerException e = new JobManagerException(ComputeErrorCodes.DYNAMODB_TRANSACTION_CONFLICT, "message");
        doThrow(e).when(scheduleJobHelper).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
        scheduler.run();
        verify(scheduleJobHelper, times(1)).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
    }

    @Test
    public void testUnhappyUnknownJobManagerException() throws JobManagerException {
        doReturn(job).when(dynamoDBJobClient).peekJob(anyString());
        final JobManagerException e = new JobManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, "message");
        doThrow(e).when(scheduleJobHelper).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
        scheduler.run();
        verify(scheduleJobHelper, times(1)).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
    }

    @Test
    public void testUnhappyUnknownUnexpectedException() throws JobManagerException {
        doReturn(job).when(dynamoDBJobClient).peekJob(anyString());
        final RuntimeException e = new RuntimeException("message");
        doThrow(e).when(scheduleJobHelper).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
        scheduler.run();
        verify(scheduleJobHelper, times(1)).scheduleJob(any(MDCLoader.class), any(JobDBRecord.class), nullable(ServiceResources.WorkerResources.class));
    }
}
