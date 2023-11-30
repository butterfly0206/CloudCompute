package com.autodesk.compute.testlambda;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;
import com.autodesk.compute.model.SlackMessage;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.common.StreamUtils.distinctByKey;
import static com.autodesk.compute.common.StreamUtils.takeWhile;
import static java.util.stream.Collectors.toList;

@Slf4j
public class SNSClient {
    private static final Config conf = Config.getInstance();

    private final SlackClient slack;
    private final SQSMessageStream sqs;


    public SNSClient() {
        this(new SlackClient(), new SQSMessageStream());
    }

    @Inject
    public SNSClient(final SlackClient slackClient, final SQSMessageStream sqs) {
        this.slack = slackClient;
        this.sqs = sqs;
    }

    public int processSnsMessages(final List<SlackMessage.Attachment> attachments, final List<String> completedJobs) {
        final Set<String> foundJobIds = new HashSet<>();
        final List<Message> matchedMessages = new ArrayList<>();
        if (completedJobs.isEmpty())
            return 0;
        final Stream<Message> snsMessageStream = takeWhile(
            sqs.getSqsMessageStream().filter(distinctByKey(this::getJobIdFromMessage)), new Predicate<>() {
                boolean foundAll; //false

                @Override
                public boolean test(final Message message) {
                    if (foundAll)   // Don't bother to parse anything more; got 'em all
                        return false;
                    final String jobId = getJobIdFromMessage(message);
                    if (foundJobIds.add(jobId)) {
                        if (completedJobs.contains(jobId)) {
                            matchedMessages.add(message);
                        }
                        if (matchedMessages.size() == completedJobs.size())
                            foundAll = true;
                        return true;
                    } else {
                        return !foundAll;
                    }
                }
            });

        final List<Message> processedMessages = snsMessageStream.collect(toList());

        if (matchedMessages.size() != completedJobs.size()) {
            // We missed some messages for jobs and need to report them.
            final String message = makeString("Only ", matchedMessages.size(), " out of ", completedJobs.size(), " messages were found in SQS");
            log.info(message);
            attachments.add(slack.toAttachment(message, SlackMessage.MessageSeverity.WARNING));
        } else {
            log.info("Found all {} job ids in SQS", completedJobs.size());
        }
        final Pair<Integer, List<BatchResultErrorEntry>> deleteResults = deleteSqsMessages(processedMessages, matchedMessages);
        return deleteResults.getLeft();
    }

    public AmazonSQS makeSQSClient() {
        return makeStandardClient(AmazonSQSClientBuilder.standard());
    }

    public List<Message> oldMessages(final List<Message> allMessages) {
        final long fifteenMinutesAgo = Instant.now().toEpochMilli() - 15 * 60 * 1_000L;

        // Only delete SQS messages that are more than fifteen minutes old, or things we just matched.
        return allMessages.stream().filter(oneMessage -> {
            final long sentTimestamp = Long.parseLong(oneMessage.getAttributes().get("SentTimestamp"));
            return sentTimestamp < fifteenMinutesAgo;
        }).collect(toList());

    }

    public Pair<Integer, List<BatchResultErrorEntry>> deleteSqsMessages(final List<Message> onlyMessagesToDelete) {
        final AmazonSQS sqsClient = makeSQSClient();

        // SQS message deletes can only be sent in batches of 10
        final List<List<Message>> messageBatches = Lists.partition(onlyMessagesToDelete, 10);

        final List<DeleteMessageBatchRequest> requests = messageBatches.stream().map(oneBatch ->
                new DeleteMessageBatchRequest().withQueueUrl(conf.getSqsEndpointUrl()).withEntries(
                        oneBatch.stream().map(oneMessage -> new DeleteMessageBatchRequestEntry()
                                .withReceiptHandle(oneMessage.getReceiptHandle())
                                .withId(oneMessage.getMessageId())).collect(toList()))
        ).collect(toList());
        final List<DeleteMessageBatchResult> results = requests.stream().map(sqsClient::deleteMessageBatch).collect(toList());
        final List<BatchResultErrorEntry> errors = results.stream().flatMap(oneResult -> oneResult.getFailed().stream()).collect(toList());
        if (!errors.isEmpty()) {
            log.warn(makeString(errors.size(), " errors reported when deleting messages from the SQS queue: ", String.join(",", errors.stream().map(BatchResultErrorEntry::toString).collect(toList()))));
        }
        return Pair.of(onlyMessagesToDelete.size(), errors);
    }

    public Pair<Integer, List<BatchResultErrorEntry>> deleteSqsMessages(final List<Message> allMessages, final List<Message> matchedMessages) {
        final List<Message> messagesToDelete = Stream.concat(
                oldMessages(allMessages).stream(), matchedMessages.stream())
                .collect(toList());
        return deleteSqsMessages(messagesToDelete);
    }

    public String getJobIdFromMessage(final Message message) {
        try {
            final JSONParser parser = new JSONParser();
            final JSONObject messageBody = (JSONObject)parser.parse(message.getBody());
            // Check if jobId is in MessageAttributes (legacy format)
            final JSONObject attributes = (JSONObject)messageBody.getOrDefault("MessageAttributes", new JSONObject());
            if (attributes.containsKey("JobID")) {
                final JSONObject jobID = (JSONObject) attributes.getOrDefault("JobID", new JSONObject());
                return jobID.getOrDefault("Value", "").toString();
            } else {  // else it's json format
                final String messageStr = (String) messageBody.getOrDefault("Message", "{}");
                final JSONObject messageObj = (JSONObject) parser.parse(messageStr);
                return (String) messageObj.getOrDefault("jobID", "");
            }
        } catch (final ParseException e) {
            log.error("Json parsing of sqs message body failed", e);
            return "";
        }
    }
}
