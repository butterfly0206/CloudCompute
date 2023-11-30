package com.autodesk.compute.model;

import com.autodesk.compute.model.dynamodb.JobDBRecord;
import lombok.Data;

import java.util.List;

@Data
public class BatchJobCreationArgs {
    private final String jobId;
    private final List<JobDBRecord> jobDBRecords;
    private final String service;
    private final String worker;
    private final String portfolioVersion;
    private final String idempotencyId;
    private final String payload;
    private final List<String> tags;
}