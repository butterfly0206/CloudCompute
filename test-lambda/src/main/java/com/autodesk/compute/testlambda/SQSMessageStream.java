package com.autodesk.compute.testlambda;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;

public class SQSMessageStream implements Supplier<Spliterator<Message>> {
    private List<Message> currentPage; // = null;
    private int currentPosition; // = 0;
    private boolean finished; // = false;
    private static final Config conf = Config.getInstance();

    private List<Message> loadOnePage() {
        currentPage = getOneBatchSqsNotifications();
        currentPosition = 0;
        return currentPage;
    }

    public AmazonSQS getSQSClient() {
        return  makeStandardClient(AmazonSQSClientBuilder.standard());
    }

    private List<Message> getOneBatchSqsNotifications() {
        AmazonSQS sqs = getSQSClient();
        // Get the next 10 messages off the queue with no waiting. Let everyone else see them also - when
        // there are simultaneous job lambdas processing that seems to be necessary to avoid message races.
        ReceiveMessageRequest request = new ReceiveMessageRequest()
                .withQueueUrl(conf.getSqsEndpointUrl())
                .withMaxNumberOfMessages(10)    // Maximum 10 per request
                .withAttributeNames("All")
                .withMessageAttributeNames("All")
                .withVisibilityTimeout(2)   // Let everyone see all the messages after 2 seconds
                .withWaitTimeSeconds(1);    // Return after one second if nothing found

        return sqs.receiveMessage(request).getMessages();
    }

    // A spliterator is the mechanism that the Java Stream classes use for implementing streams of
    // finite length. This spliterator loads pages of SQS messages and returns them one at a time,
    // loading more only when we get to the end of this page's requests.
    @Override
    public Spliterator<Message> get() {
        return new Spliterators.AbstractSpliterator<Message>(50, Spliterator.NONNULL) {
            @Override
            public boolean tryAdvance(Consumer<? super Message> action) {
                    // finished, currentPage, and currentPosition are all captured from the supplier class.
                    if (finished)
                        return false;
                    // Load more data if needed.
                    if (currentPage == null || currentPosition >= currentPage.size())
                        loadOnePage();
                    // If we still are past the end of the list, then we're done.
                    if (currentPosition >= currentPage.size()) {
                        finished = true;
                        return false;
                    }
                    // Found something. Apply the action to it, then increment the position counter.
                    action.accept(currentPage.get(currentPosition));
                        ++currentPosition;
                        return true;
                    }
                };
        }

    // Return a stream of SQS Messages, automatically paging the results if needed.
    public Stream<Message> getSqsMessageStream() {
        return StreamSupport.stream(this, Spliterator.NONNULL,false);
    }
}