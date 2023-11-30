package com.autodesk.compute.monitor;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.autodesk.compute.testlambda.SQSMessageStream;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.Mockito.*;
import static com.autodesk.compute.test.TestHelper.*;

public class TestSQSMessageStream {
    @Mock
    private AmazonSQS sqs;

    @Spy
    private SQSMessageStream stream;

    public TestSQSMessageStream() {
        MockitoAnnotations.openMocks(this);
        when(stream.getSQSClient()).thenReturn(sqs);
    }

    private Message makeMessage(String body) {
        return new Message().withBody(body);
    }

    private List<Message> makeMessages(int messages) {
        return IntStream.range(0, messages)
                .mapToObj(Integer::toString)
                .map(this::makeMessage)
                .collect(Collectors.toList());
    }

    public ReceiveMessageResult resultWithMessages(int messages) {
        return new ReceiveMessageResult().withMessages(makeMessages(messages));
    }

    @Test
    public void emptyMessages() {
        ReceiveMessageResult stubbedReturn = resultWithMessages(0);
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(stubbedReturn);
        List<Message> messages = stream.getSqsMessageStream().collect(Collectors.toList());
        Assert.assertEquals(0, messages.size());
    }

    @Test
    public void onePageOfMessages() {
        ReceiveMessageResult[] stubbedReturns = { resultWithMessages(3), resultWithMessages(0) };
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenAnswer(makeAnswer(stubbedReturns));

        List<Message> messages = stream.getSqsMessageStream().collect(Collectors.toList());
        Assert.assertEquals(3, messages.size());
    }

    @Test
    public void multiplePagesOfMessages() {
        ReceiveMessageResult[] stubbedReturns = { resultWithMessages(3), resultWithMessages(4), resultWithMessages(0) };

        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenAnswer(makeAnswer(stubbedReturns));
        List<Message> messages = stream.getSqsMessageStream().collect(Collectors.toList());
        Assert.assertEquals(7, messages.size());
    }

}
