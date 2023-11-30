package com.autodesk.compute.model.cosv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.swagger.annotations.ApiModelProperty;
import lombok.*;

import java.util.List;

import static com.autodesk.compute.configuration.ComputeConstants.WorkerDefaults;

@Data
@Builder
@AllArgsConstructor
@JsonDeserialize(builder = ComputeSpecification.ComputeSpecificationBuilder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComputeSpecification {
    @JsonProperty("workerName")
    @ApiModelProperty(required = true, value = "Name of the CloudOS-Compute worker.")
    private String workerName;

    @JsonProperty("inputSchema")
    @ApiModelProperty(required = true, value = "JSON schema for the input to CloudOS-Compute job.")
    private Object inputSchema;

    @JsonProperty("outputSchema")
    @ApiModelProperty(required = true, value = "JSON schema for the output of CloudOS-Compute job.")
    private Object outputSchema;

    @JsonProperty("progressDetailsSchema")
    @ApiModelProperty(value = "JSON schema for the progress details of CloudOS-Compute job.")
    private Object progressDetailsSchema;

    @JsonProperty("heartbeatTimeout")
    @ApiModelProperty(value = "Time in seconds that a job can run without heartbeating before it is timed out. Default = 120 sec.", allowableValues = "range[5,3600]")
    private Integer heartbeatTimeoutSeconds;

    @JsonProperty("jobAttempts")
    @ApiModelProperty(value = "Number of attempts to make when job fails.", allowableValues = "range[1,10]")
    private Integer jobAttempts;

    @With
    @JsonProperty("jobTimeout")
    @ApiModelProperty(value = "Time in seconds that a job is allowed to execute before it is timed out. Default = 3600 sec.", allowableValues = "range[1,2419200]")
    private Integer jobTimeoutSeconds;

    @With
    @JsonProperty("notification")
    @ApiModelProperty(value = "Notification method for jobs")
    private Notification notification;

    @Singular("jobConcurrencyLimit")
    @JsonProperty("jobConcurrencyLimits")
    @ApiModelProperty(required = true, value = "List of concurrent jobs limits by user type")
    private List<NameValueDefinition<Integer>> jobConcurrencyLimits;

    @JsonProperty("useServiceIntegration")
    @ApiModelProperty(value = "This feature allows compute worker to avoid polling for job, instead it will be provided in environment when worker starts.")
    private Boolean useServiceIntegration;

    @Singular("jobQueue")
    @JsonProperty("jobQueues")
    @ApiModelProperty(required = false, value = "List of concurrent jobs limits by user type")
    private List<NameValueDefinition<String>> jobQueues;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComputeSpecificationBuilder {
        // NOTE: WorkerDefaults constants in com/autodesk/compute/configuration/ComputeConstants.java
        // should be injected here as member data, but the current IDE plugins and whatnot are
        // getting in the way of that implementation by forcing 'final' in pre-initialized
        // fields.  Ideally, we want the custom builder to handle custom init - see discussion here:
        // https://www.baeldung.com/lombok-builder-default-value.  To that end, and to keep the
        // business goo here, we supply:
        public static ComputeSpecification buildWithDefaults() {
            return ComputeSpecification.builder().jobAttempts(WorkerDefaults.DEFAULT_WORKER_JOB_ATTEMPTS)
                    .heartbeatTimeoutSeconds(WorkerDefaults.DEFAULT_WORKER_HEARTBEAT_TIMEOUT_SECONDS)
                    .jobTimeoutSeconds(WorkerDefaults.DEFAULT_WORKER_JOB_TIMEOUT_SECONDS)
                    .build();
        }

    }

    public enum NotificationType {
        SNS("sns");

        private final String value;

        NotificationType(final String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }
    }

    public enum MessageFormat {
        LEGACY("legacy"),
        JSON("json");

        private final String value;

        MessageFormat(final String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }
    }


    @Value
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Notification {
        @JsonProperty("endpoint")
        @ApiModelProperty(required = true, value = "The URL/ARN of the endpoint")
        private String endpoint;

        @JsonProperty("notificationType")
        @ApiModelProperty(required = true, value = "Kind of service to use for notification")
        private NotificationType notificationType;

        @JsonProperty("messageFormat")
        @ApiModelProperty(value = "What format to use for the message")
        private MessageFormat messageFormat;

        @JsonPOJOBuilder(withPrefix = "")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class NotificationBuilder {
        }
    }

}
