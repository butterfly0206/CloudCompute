package com.autodesk.compute.model;

import com.autodesk.compute.common.model.Job;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RunJobResult {
    private HealthReport.TestResult testResult;
    private Job job;
}
