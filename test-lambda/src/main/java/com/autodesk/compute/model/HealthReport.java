package com.autodesk.compute.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HealthReport {

    public enum TestResult {
        SUCCESS,
        FAILED_TO_SUBMIT,
        FAILED,
        NOT_CONFIGURED_HERE
    }

    private TestResult batchJobStatus;
    private TestResult ecsJobStatus;
    private TestResult gpuJobStatus;
    private TestResult jobSearchStatus;
    private TestResult snsNotificationStatus;
    private TestResult queuedEcsJobsStatus;
    private TestResult queuedBatchJobsStatus;
}
