package com.autodesk.compute.common;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.cloudwatchevents.AmazonCloudWatchEvents;
import com.amazonaws.services.cloudwatchevents.AmazonCloudWatchEventsClientBuilder;
import com.amazonaws.services.cloudwatchevents.model.PutEventsRequest;
import com.amazonaws.services.cloudwatchevents.model.PutEventsRequestEntry;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.util.Date;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.common.ComputeStringOps.makeString;


@Slf4j
public class CloudWatchClient {
    protected final AmazonCloudWatchEvents amazonCloudWatchEvents;

    @Inject
    public CloudWatchClient() {
        this(makeStandardClient(AmazonCloudWatchEventsClientBuilder.standard()));
    }

    public CloudWatchClient(final AmazonCloudWatchEvents eventsClient) {
        amazonCloudWatchEvents = eventsClient;
    }

    public void createEvent(
            @NonNull final String jobId,
            @NonNull final String service,
            @NonNull final String worker,
            @NonNull final String jobstatus,
            @NonNull final String source) {
        try {
            final ObjectNode eventDetails = Json.mapper.createObjectNode();
            eventDetails.put("jobID", jobId);
            eventDetails.put("service", service);
            eventDetails.put("worker", worker);
            eventDetails.put("status", jobstatus);

            final String event = eventDetails.toPrettyString();

            final PutEventsRequestEntry requestEntry = new PutEventsRequestEntry()
                    .withTime(new Date())
                    .withDetail(event)
                    .withDetailType("Step Functions Execution Status Change")
                    .withSource(source);

            final PutEventsRequest request = new PutEventsRequest()
                    .withEntries(requestEntry);

            amazonCloudWatchEvents.putEvents(request);
        } catch (final AmazonClientException ace) {
            final String error = makeString("The following error occurred while adding custom event for ",
                    jobId, " with source ", source, ace.getMessage(), ace.getCause());
            log.error(error, ace);
        }
    }
}


