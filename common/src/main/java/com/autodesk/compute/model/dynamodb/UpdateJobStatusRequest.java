package com.autodesk.compute.model.dynamodb;

import com.autodesk.compute.common.model.Failure;
import com.autodesk.compute.dynamodb.JobStatus;
import lombok.*;

import java.util.List;

@With
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateJobStatusRequest {
    private String jobId;
    private String idempotencyId;
    private JobStatus status;
    private String stepExecutionArn;
    private List<Failure> failures;
    private String output;
    private String userServiceWorker;
}