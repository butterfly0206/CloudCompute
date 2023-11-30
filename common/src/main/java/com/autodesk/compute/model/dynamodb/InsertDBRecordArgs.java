package com.autodesk.compute.model.dynamodb;

import com.amazonaws.util.StringUtils;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.ServiceWithVersion;
import com.autodesk.compute.discovery.ServiceResources;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.model.cosv2.ComputeSpecification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

import static com.autodesk.compute.common.ComputeStringOps.compressString;
import static com.google.common.base.MoreObjects.firstNonNull;

@Data
@AllArgsConstructor
public class InsertDBRecordArgs {
    private final String service;
    private final String worker;
    private final String jobId;
    private final String oxygenToken;
    private final String serviceClientId;
    private final ServiceResources.WorkerResources workerResources;
    private final String userId;
    private final String userType;
    private final boolean isBatch;
    private final List<String> tags;
    private final Object payload;
    private final String idempotencyId;
    private final String batchQueueType;

    public JobDBRecord toJobDBRecord() throws JsonProcessingException {
        return toJobDBRecord(this);
    }

    public static JobDBRecord toJobDBRecord(final InsertDBRecordArgs insertDBRecordArgs) throws JsonProcessingException {
        final ComputeSpecification defaultValues = ComputeSpecification.ComputeSpecificationBuilder.buildWithDefaults();
        final JobDBRecord record = JobDBRecord.createDefault(insertDBRecordArgs.getJobId());
        record.setStepExecutionArns(Lists.newArrayList());
        record.setService(insertDBRecordArgs.getService());
        record.setServiceClientId(insertDBRecordArgs.getServiceClientId());
        record.setWorker(insertDBRecordArgs.getWorker());
        record.setPortfolioVersion(insertDBRecordArgs.getWorkerResources().getPortfolioVersion());

        if (insertDBRecordArgs.getWorkerResources().getDeploymentType() == ServiceResources.ServiceDeployment.TEST) {
            record.setResolvedPortfolioVersion(insertDBRecordArgs.getWorkerResources().getPortfolioVersion());
        } else {
            record.setResolvedPortfolioVersion(ServiceWithVersion.DEPLOYED);
        }

        final long currentTimeMillis = JobDBRecord.fillCurrentTimeMilliseconds();
        record.setModificationTime(currentTimeMillis);
        record.setTagsModificationTime(currentTimeMillis);
        // Careful here! We're using isBatch as an override to the batch settings when we're doing tests.
        // If isBatch is false, even though we're routing to a batch worker, we're expecting the job to be polling.
        // That's why this uses 'isBatch' instead of instance type checking on the workerResources object.
        record.setHeartbeatTimeoutSeconds(firstNonNull(
                insertDBRecordArgs.getWorkerResources().getComputeSpecification().getHeartbeatTimeoutSeconds(),
                defaultValues.getHeartbeatTimeoutSeconds()));
        record.setLastHeartbeatTime(0L); // Never sent a heartbeat before
        record.setCreationTime(currentTimeMillis);
        record.setJobFinishedTime(0L); // Not completed yet
        record.setHeartbeatStatus(DynamoDBJobClient.HeartbeatStatus.ENABLED.toString());
        record.setCurrentRetry(0); // First time
        record.setIdempotencyId(insertDBRecordArgs.getIdempotencyId());
        record.setUserId(insertDBRecordArgs.getUserId());
        record.setUserType(insertDBRecordArgs.getUserType());
        record.setBatch(insertDBRecordArgs.isBatch());
        record.setBatchQueueType(insertDBRecordArgs.getBatchQueueType());

        final ComputeSpecification.Notification notification = insertDBRecordArgs.getWorkerResources().getComputeSpecification().getNotification();
        if (notification != null) {
            record.setJobStatusNotificationType(notification.getNotificationType().toString());
            record.setJobStatusNotificationEndpoint(notification.getEndpoint());
            if (notification.getMessageFormat() != null) {
                record.setJobStatusNotificationFormat(notification.getMessageFormat().toString());
            }
        }
        if (insertDBRecordArgs.getTags() != null)
            record.setTags(new ArrayList<>(insertDBRecordArgs.getTags()));

        if (!StringUtils.isNullOrEmpty(insertDBRecordArgs.getOxygenToken()))
            record.setOxygenToken(insertDBRecordArgs.getOxygenToken());

        final String jsonPayload = Json.mapper.writeValueAsString(insertDBRecordArgs.getPayload());
        record.setBinaryPayload(compressString(jsonPayload));
        return record;
    }
}
