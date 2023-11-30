package com.autodesk.compute.monitor;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchResult;
import com.amazonaws.services.sqs.model.DeleteMessageBatchResultEntry;
import com.amazonaws.services.sqs.model.Message;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.model.SlackMessage;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.testlambda.SNSClient;
import com.autodesk.compute.testlambda.SQSMessageStream;
import com.autodesk.compute.testlambda.SlackClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.test.TestHelper.makeAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Slf4j
@Category(UnitTests.class)
public class TestSNSClient {
    @Mock
    private SQSMessageStream sqs;

    @Mock
    private SlackClient slack;

    @Spy
    @InjectMocks
    private SNSClient sns;

    @Mock
    private AmazonSQS sqsClient;

    public TestSNSClient() {
        MockitoAnnotations.openMocks(this);
        when(sns.makeSQSClient()).thenReturn(sqsClient);
    }

    private Message makeMessage(final String prefix, final int number, final int messageAgeSeconds) {
        final String message = makeString(
                "{ \"MessageAttributes\": { \"JobID\": { \"Value\": \"",
                prefix, number, "\" } } }");
        final HashMap<String, String> messageAttributes = Maps.newHashMap();
        messageAttributes.put("SentTimestamp",
                Long.toString(System.currentTimeMillis() - 1_000 * messageAgeSeconds));
        return new Message()
                .withBody(message)
                .withAttributes(messageAttributes);
    }

    private String bodyWithID(final String prefix, final int number) {
        return makeString("{ \\\"jobID\\\" : \\\"", prefix, number, "\\\" }");
    }

    @SneakyThrows(JsonProcessingException.class)
    private Message makeMessageWithIdInBody(final String prefix, final int number, final int messageAgeSeconds) {
        final String message = makeString(
                "{ \"Message\": \"", bodyWithID(prefix, number), "\", \"MessageAttributes\": {  } }\"");
        Json.mapper.readTree(message);
        final HashMap<String, String> messageAttributes = Maps.newHashMap();
        messageAttributes.put("SentTimestamp",
                Long.toString(System.currentTimeMillis() - 1_000 * messageAgeSeconds));
        return new Message()
                .withBody(message)
                .withAttributes(messageAttributes);
    }


    private List<Message> makeMessages(final String prefix, final int messages, final int messageAge) {
        return IntStream.range(0, messages)
                .mapToObj(num -> {
                    if (num % 2 == 0)
                        return makeMessage(prefix, num, messageAge);
                    else
                        return makeMessageWithIdInBody(prefix, num, messageAge);
                })
                .collect(Collectors.toList());
    }

    @Test
    public void noCompletedJobsNoMessages() {
        final ArrayList<SlackMessage.Attachment> attachments = new ArrayList<>();
        final int messagesDeleted = sns.processSnsMessages(attachments, Collections.emptyList());
        Assert.assertEquals("No messages should be deleted", 0, messagesDeleted);
        verify(sqs, never()).getSqsMessageStream();
    }

    @Test
    public void oneCompletedJob() {
        final ArrayList<SlackMessage.Attachment> attachments = new ArrayList<>();
        // Four SQS messages, with job ids "1" - "4".
        final Stream<Message> messages = makeMessages("", 4, 300).stream();
        when(sqs.getSqsMessageStream()).thenReturn(messages);
        // A successful delete call.
        final DeleteMessageBatchResult[] deleteResults = {
                new DeleteMessageBatchResult().withSuccessful(
                        new DeleteMessageBatchResultEntry().withId("1")
                )
        };
        when(sqsClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(makeAnswer(deleteResults));
        // Matched one message in the list.
        final int deletedMessages = sns.processSnsMessages(attachments, Lists.newArrayList("1"));
        Assert.assertEquals( "One job should have matched", 1, deletedMessages);
        verify(sqs, times(1)).getSqsMessageStream();
    }

    @Test
    public void deleteOldMessages() {
        final ArrayList<SlackMessage.Attachment> attachments = new ArrayList<>();
        // Four messages older than 900 seconds.
        final List<Message> oldMessages = makeMessages("O", 4, 1200);
        // Five messages newer than 900 seconds.
        final List<Message> newMessages = makeMessages("N", 5, 300);
        final ArrayList<Message> merged = new ArrayList<>(oldMessages);
        merged.addAll(newMessages);
        when(sqs.getSqsMessageStream()).thenReturn(merged.stream());
        // One successful API call to DeleteMessageBatch. The empty
        // ArrayList for failed is important - NPE otherwise.
        final DeleteMessageBatchResult[] deleteResults = {
                new DeleteMessageBatchResult().withSuccessful(
                        new DeleteMessageBatchResultEntry().withId("1")
                ).withFailed(new ArrayList<>())
        };
        when(sqsClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenAnswer(makeAnswer(deleteResults));
        // Complete one of the new messages, exposing the old ones for deletion
        final int deletedMessages = sns.processSnsMessages(attachments, Lists.newArrayList("N4", "Missing"));
        // Expect to delete: the four old messages, plus the one that matched N4.
        Assert.assertEquals(5, deletedMessages);
        // Missing was not present, so there should be a message about it.
        Assert.assertEquals("Warning message should have been put in", 1, attachments.size());
    }

}
