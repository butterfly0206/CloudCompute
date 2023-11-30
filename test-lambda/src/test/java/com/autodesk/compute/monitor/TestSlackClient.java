package com.autodesk.compute.monitor;

import com.autodesk.compute.common.ComputeUtils;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.model.SlackMessage;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.testlambda.SlackClient;
import com.autodesk.compute.testlambda.gen.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.mockito.Mockito.*;

@Slf4j
@Category(UnitTests.class)
public class TestSlackClient {

    @Mock
    private final HttpResponse<String> httpResponse = Mockito.mock(HttpResponse.class);

    @Spy
    private SlackClient slackClient;

    public TestSlackClient() {
    }

    @Before
    @SneakyThrows(IOException.class)
    public void init() {
        MockitoAnnotations.openMocks(this);
        slackClient = Mockito.spy(new SlackClient(ComputeUtils.TimeoutSettings.FAST));
        Mockito.doReturn(httpResponse).when(slackClient).executeRequest(any(HttpRequest.class));
    }

    @Test
    @SneakyThrows(IOException.class)
    public void noAttachments() {
        when(httpResponse.statusCode()).thenReturn(200);
        slackClient.postToSlack(Collections.emptyList());
        verify(slackClient, times(1)).executeRequest(any(HttpRequest.class));
    }

    @Test
    @SneakyThrows(IOException.class)
    public void allAttachmentTypes() {
        final List<SlackMessage.Attachment> attachments = Arrays.asList(
                slackClient.toAttachment("One", SlackMessage.MessageSeverity.GOOD),
                slackClient.toAttachment("Two", new Exception("Hello!")),
                slackClient.toAttachment("Three", new ApiException("ApiException"))
        );
        when(httpResponse.statusCode()).thenReturn(200);
        slackClient.postToSlack(attachments);
        verify(slackClient, times(1)).executeRequest(any(HttpRequest.class));
    }

    @Test
    @SneakyThrows(IOException.class)
    public void badResponseOk() {
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn("Bad Request (from test)");
        slackClient.postToSlack(Collections.emptyList());
        verify(slackClient, times(1)).goodResponse(any(HttpResponse.class));
        verify(slackClient, times(1)).executeRequest(any(HttpRequest.class));
    }

    @Test
    @SneakyThrows(IOException.class)
    public void swallowIOException() {
        Mockito.doAnswer(invocation -> {
            throw new IOException("Bad Request (from test)");
        }).when(slackClient).executeRequest(any(HttpRequest.class));
        slackClient.postToSlack("Ambiguous message");
        verify(slackClient, times(0)).goodResponse(any(HttpResponse.class));
    }

    @Test
    public void validateJson() throws JsonProcessingException {
        final List<SlackMessage.Attachment> attachments = Arrays.asList(
                slackClient.toAttachment("One", SlackMessage.MessageSeverity.GOOD),
                slackClient.toAttachment("Two", new Exception("Hello!")),
                slackClient.toAttachment("Three", new ApiException("ApiException"))
        );
        final String jsonString = slackClient.makeSlackMessageJson(attachments);
        final SlackMessage parsedJson = Json.mapper.readValue(jsonString, SlackMessage.class);
        Assert.assertEquals(3, parsedJson.getAttachments().size());
        Assert.assertTrue(Objects.deepEquals(parsedJson.getAttachments(), attachments));
    }

}
