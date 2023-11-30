package com.autodesk.compute.common;

import com.amazonaws.services.cloudwatchevents.AmazonCloudWatchEvents;
import com.amazonaws.services.cloudwatchevents.model.AmazonCloudWatchEventsException;
import com.amazonaws.services.cloudwatchevents.model.PutEventsRequest;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestCloudWatchClient {
    @Mock
    private AmazonCloudWatchEvents cw;

    @Spy
    @InjectMocks
    private CloudWatchClient client;

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void HappyPathDoesNotThrow() {
        client.createEvent("jobid", "service", "worker", "status", "source");
        verify(cw, times(1)).putEvents(any(PutEventsRequest.class));
    }

    @Test
    public void ErrorPathDoesNotThrow() {
        when(cw.putEvents(any(PutEventsRequest.class))).thenThrow(new AmazonCloudWatchEventsException("Failed"));
        client.createEvent("jobid", "service", "worker", "status", "source");
        verify(cw, times(1)).putEvents(any(PutEventsRequest.class));
    }

}
