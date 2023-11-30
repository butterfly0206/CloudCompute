package com.autodesk.compute.model.dynamodb;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchJobsResult {
    private List<JobDBRecord> jobs;
    private String nextToken;
}
