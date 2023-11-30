package com.autodesk.compute.model.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.util.StringUtils;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.dynamodb.JobStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.util.internal.StringUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.configuration.ComputeConstants.DynamoFields.*;

@Data
@NoArgsConstructor // DynamoDB requires a no-args constructor
@DynamoDBTable(tableName = PROGRESS_TABLE_NAME)
public class JobDBRecord {
    // Keep jobs in database for one month
    public static final long JOB_RETENTION_TIME_SECONDS = TimeUnit.DAYS.toSeconds(30);
    private static final String ARRAY = "array";

    /*
     Required attributes
     */
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBAttribute(attributeName = JOB_CREATED_TIME)
    private long creationTime;
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBHashKey(attributeName = JOB_ID)
    private String jobId;
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBAttribute(attributeName = STATUS)
    private String status;
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBAttribute(attributeName = WORKER)
    private String worker;
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBAttribute(attributeName = SERVICE)
    private String service;
    @DynamoDBAttribute(attributeName = IS_BATCH_JOB)
    private boolean isBatch;
    @Setter(onMethod = @__(@NonNull))
    @DynamoDBAttribute(attributeName = TTL)
    private Long ttl;

    /*
     Situational attributes
     */
    @DynamoDBAttribute(attributeName = HEARTBEAT_STATUS)
    private String heartbeatStatus;
    @DynamoDBAttribute(attributeName = CURRENT_RETRY)
    private Integer currentRetry;                   // Boxed, because it might not be present
    @DynamoDBAttribute(attributeName = HEARTBEAT_TIMEOUT_SECONDS)
    private Integer heartbeatTimeoutSeconds;        // Boxed, because it might not be present
    @DynamoDBAttribute(attributeName = PERCENT_COMPLETE)
    private Integer percentComplete;                // Boxed, because it might not be present
    @DynamoDBAttribute(attributeName = ERRORS)
    private List<String> errors;
    @DynamoDBAttribute(attributeName = TAGS)
    private List<String> tags;
    @DynamoDBAttribute(attributeName = TAGS_MODIFICATION_TIME)
    private Long tagsModificationTime;
    // TODO: Remove when binaryDetails have been pushed to PROD for over a month
    @DynamoDBAttribute(attributeName = DETAILS)
    private String details;
    @DynamoDBAttribute(attributeName = BINARY_DETAILS)
    private ByteBuffer binaryDetails;
    @DynamoDBAttribute(attributeName = JOB_STATUS_NOTIFICATION_ENDPOINT)
    private String jobStatusNotificationEndpoint;   // From the Notification part of the Compute Spec
    @DynamoDBAttribute(attributeName = JOB_STATUS_NOTIFICATION_TYPE)
    private String jobStatusNotificationType;       // From the Notification part of the Compute Spec
    @DynamoDBAttribute(attributeName = JOB_STATUS_NOTIFICATION_FORMAT)
    private String jobStatusNotificationFormat;     // From the Notification part of the Compute Spec
    @DynamoDBAttribute(attributeName = OXYGEN_TOKEN)
    private String oxygenToken;
    // TODO: Remove when binaryPayload has been pushed to PROD for over a month
    @Setter(onMethod=@__(@NonNull))
    @DynamoDBAttribute(attributeName = PAYLOAD)
    private String payload;
    @DynamoDBAttribute(attributeName = BINARY_PAYLOAD)
    private ByteBuffer binaryPayload;
    @DynamoDBAttribute(attributeName = PORTFOLIO_VERSION)
    private String portfolioVersion;
    @DynamoDBAttribute(attributeName = RESOLVED_PORTFOLIO_VERSION)
    private String resolvedPortfolioVersion;

    // TODO: Remove when binaryRseult has been pushed to PROD for over a month
    @DynamoDBAttribute(attributeName = RESULT)
    private String result;
    @DynamoDBAttribute(attributeName = BINARY_RESULT)
    private ByteBuffer binaryResult;
    @DynamoDBAttribute(attributeName = SERVICE_CLIENT_ID)
    private String serviceClientId;
    @DynamoDBAttribute(attributeName = SPAWNED_BATCH_JOB_ID)
    private String spawnedBatchJobId;
    @DynamoDBAttribute(attributeName = STEP_EXECUTION_ARNS)
    private List<String> stepExecutionArns;
    @DynamoDBAttribute(attributeName = JOB_FINISHED_TIME)
    private Long jobFinishedTime;
    @DynamoDBAttribute(attributeName = LAST_HEARTBEAT_TIME)
    private Long lastHeartbeatTime;
    @DynamoDBAttribute(attributeName = MODIFICATION_TIME)
    private Long modificationTime;
    // Since the original implementation stores it as a list of strings, we have no choice but to treat is as such
    @DynamoDBAttribute(attributeName = STATUS_UPDATES)
    private List<String> statusUpdates;
    @Setter(onMethod = @__(@NonNull))
    @DynamoDBAttribute(attributeName = IDEMPOTENCY_ID)
    private String idempotencyId;
    @Setter(onMethod = @__(@NonNull))
    @DynamoDBAttribute(attributeName = USER_ID)
    private String userId;
    @Setter(onMethod = @__(@NonNull))
    @DynamoDBAttribute(attributeName = USER_TYPE)
    private String userType;
    @Setter(onMethod = @__(@NonNull))
    @DynamoDBAttribute(attributeName = BATCH_QUEUE_TYPE)
    private String batchQueueType;
    // Job queue related attributes
    @DynamoDBAttribute(attributeName = QUEUE_ID)
    private String queueId;
    @DynamoDBAttribute(attributeName = JOB_ENQUEUE_TIME)
    private Long enqueueTime;
    @DynamoDBAttribute(attributeName = QUEUE_INFO)
    private QueueInfo queueInfo;
    @DynamoDBAttribute(attributeName = ARRAY_JOB_ID)
    private String arrayJobId;
    @DynamoDBAttribute(attributeName = ARRAY_INDEX)
    private long arrayJobIndex;

    public static String newJobId() {
        return java.util.UUID.randomUUID().toString();
    }

    public static String newArrayJobId() {
        return makeString(newJobId(), ":", ARRAY);
    }

    public static final Pattern JOB_ID_PATTERN = Pattern
            .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(:array(:(0|([1-9]\\d{0,3})))?)?");

    public static boolean isValidJobId(final String jobId) {
        return JOB_ID_PATTERN.matcher(jobId).matches();
    }

    // Array job ID is just <guid>:array without any index
    public static final Pattern ARRAY_JOB_ID_PATTERN = Pattern
            .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}:array");

    public static boolean isValidArrayJobId(final String jobId) {
        return ARRAY_JOB_ID_PATTERN.matcher(jobId).matches();
    }

    public static String createChildArrayJobId(final String arrayJobId, final int index) {
        return makeString(arrayJobId, ":", index);
    }


    public static long fillJobTtlSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + JOB_RETENTION_TIME_SECONDS;
    }

    private static String createUserServiceWorkerId(final String userId, final String service, final String worker) {
        // all arguments must be present else return null
        return makeString(userId, "|", service, "|", worker);
    }

    // TODO: Does this method belong here?
    public static long fillCurrentTimeMilliseconds() {
        return System.currentTimeMillis();
    }

    // TODO: Does this method belong here?
    public static long fillCurrentTimeSeconds() {
        return Instant.now().getEpochSecond();
    }

    public static String getJobStatusUpdateAsString(final String jobStatus) {
        final ObjectNode statusUpdate = Json.mapper.createObjectNode();
        statusUpdate.put("status", jobStatus);
        statusUpdate.put("timestamp", System.currentTimeMillis());
        try {
            return Json.mapper.writeValueAsString(statusUpdate);
        } catch (final JsonProcessingException e) {
            return String.format("{\"status\": \"%s\", \"timestamp\": %d}", jobStatus, System.currentTimeMillis());
        }
    }

    public static JobDBRecord createDefault(final String jobId) {
        final JobDBRecord job = new JobDBRecord();
        final long currentTimeMillis = fillCurrentTimeMilliseconds();
        job.setJobId(jobId);
        job.ttl = fillJobTtlSeconds();
        job.creationTime = currentTimeMillis;
        job.modificationTime = currentTimeMillis;
        job.tagsModificationTime = currentTimeMillis;
        job.errors = new ArrayList<>();
        job.statusUpdates = new ArrayList<>();
        return job;
    }

    public void setJobId(final String jobId) {
        if (StringUtil.isNullOrEmpty(jobId)) return;
        this.jobId = jobId;

        // Check if it's an array job -> guid:array:index
        final int i = jobId.lastIndexOf(":");
        final int j = jobId.lastIndexOf(ARRAY);
        if (j > 0 && i > j) {
            arrayJobId = jobId.substring(0, i);
            arrayJobIndex = Integer.parseInt(jobId.substring(i + 1));
        }
    }

    @DynamoDBIgnore
    public long getCreationTimeMillis() {
        return creationTime;
    }

    @DynamoDBIgnore
    public String getUserServiceWorkerId() {
        return StringUtils.isNullOrEmpty(userType) ? null : createUserServiceWorkerId(userId, service, worker);
    }

    public String findDetails() {
        if (binaryDetails != null) {
            return ComputeStringOps.uncompressString(binaryDetails);
        }
        return details;
    }

    @NonNull
    public void setBinaryDetails(final ByteBuffer binaryDetails) {
        this.binaryDetails = binaryDetails.asReadOnlyBuffer();
    }

    public String findPayload() {
        if (binaryPayload != null)
            return ComputeStringOps.uncompressString(binaryPayload);
        return payload;
    }

    @NonNull
    public void setBinaryPayload(final ByteBuffer binaryPayload) {
        this.binaryPayload = binaryPayload.asReadOnlyBuffer();
    }

    @DynamoDBAttribute(attributeName = RESULT)
    public String getResult() {
        return result;
    }

    public String findResult() {
        if (binaryResult != null)
            return ComputeStringOps.uncompressString(binaryResult);
        return result;
    }

    @DynamoDBAttribute(attributeName = RESOLVED_PORTFOLIO_VERSION)
    public String getResolvedPortfolioVersion() {
        return StringUtils.isNullOrEmpty(resolvedPortfolioVersion) ? portfolioVersion : resolvedPortfolioVersion;
    }

    @NonNull
    public void setBinaryResult(final ByteBuffer binaryResult) {
        this.binaryResult = binaryResult.asReadOnlyBuffer();
    }

    public boolean initQueueInfo(final String queueId) {
        if (!StringUtils.isNullOrEmpty(queueId)) {
            final long tsUTC = System.currentTimeMillis();
            final QueueInfo info = new QueueInfo();
            info.setInQueue(false);
            info.setSelected(false);
            info.setStatus(QueueInfo.StatusEnum.QUEUED);
            info.setInQueue(true);
            info.setAddTimestamp(tsUTC);
            info.setUpdateTimestamp(tsUTC);
            info.setVersion(1);
            setQueueInfo(info);
            setQueueId(queueId);
            setEnqueueTime(tsUTC);
            setStatus(JobStatus.QUEUED.toString());
            if(getStatusUpdates() == null) {
                setStatusUpdates(new ArrayList<>());
            }
            getStatusUpdates().add(getJobStatusUpdateAsString(JobStatus.QUEUED.toString()));
            return true;
        }
        return false;
    }
}

