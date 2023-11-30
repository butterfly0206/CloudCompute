package com.autodesk.compute.common;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;

public class ComputeSqsDriver {

    private final AmazonSQS sqs;

    public ComputeSqsDriver() {
        sqs = makeStandardClient(AmazonSQSClientBuilder.standard());
    }

    public Message receiveOneMessage(String queueUrl, Integer visibilityTimeout) {
        final ReceiveMessageRequest request =
                new ReceiveMessageRequest()
                        .withQueueUrl(queueUrl)
                        .withMaxNumberOfMessages(1)
                        .withAttributeNames("All")
                        .withMessageAttributeNames("All")
                        // No one else should see the message while we are working ont it
                        .withVisibilityTimeout(visibilityTimeout)
                        .withWaitTimeSeconds(20);
        List<Message> messages = sqs.receiveMessage(request).getMessages();
        return messages != null && !messages.isEmpty() ? messages.get(0) : null;
    }

    public void deleteMessage(String queueUrl, final Message message) {
        final DeleteMessageRequest request =
                new DeleteMessageRequest()
                        .withQueueUrl(queueUrl)
                        .withReceiptHandle(message.getReceiptHandle());
        sqs.deleteMessage(request);
    }
}
