package com.autodesk.compute.model;

import com.autodesk.compute.testlambda.Config;

public enum JobType {
    BATCH("Batch job"),
    ECS("ECS job"),
    GPU("GPU job");

    private static final Config conf = Config.getInstance();
    private final String name;

    JobType(final String jobName) {
        name = jobName;
    }

    public String getName() {
        return name;
    }

    public String getWorker() {
        switch (this) {
            case BATCH:
                return conf.getBatchWorker();
            case ECS:
                return conf.getEcsWorker();
            case GPU:
                return conf.getGpuWorker();
            default:
                return "";
        }
    }

    public int getJobCount() {
        switch (this) {
            case BATCH:
                return conf.getBatchJobsCount();
            case ECS:
                return conf.getEcsJobsCount();
            case GPU:
                return conf.getGpuJobsCount();
            default:
                return 0;
        }
    }

    public long getTimeoutSeconds() {
        if (this == JobType.ECS)
            return conf.getEcsJobTimeoutSeconds();
        return conf.getBatchJobTimeoutSeconds();
    }
}