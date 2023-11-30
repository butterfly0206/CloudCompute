package com.autodesk.compute.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.*;

import java.util.List;

@Value
@Builder
@JsonDeserialize(builder = SlackMessage.SlackMessageBuilder.class)
public class SlackMessage {

    private String text;
    private List<Attachment> attachments;
    private boolean mrkdwn;

    public enum MessageSeverity {
        GOOD ("good"), WARNING("warning"), DANGER("danger");

        private String color;

        MessageSeverity(String c) {
            color = c;
        }
        @Override
        public String toString() {
            return color;
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class SlackMessageBuilder {}

    @Value
    @Builder
    @JsonDeserialize(builder = Attachment.AttachmentBuilder.class)
    public static class Attachment {

        private String title;
        private String text;
        private String color;

        @JsonPOJOBuilder(withPrefix = "")
        public static class AttachmentBuilder {}
    }
}
