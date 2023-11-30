package com.autodesk.compute.dynamodb;

import com.autodesk.compute.common.model.Status;
import com.fasterxml.jackson.annotation.JsonValue;

public enum JobStatus {
    QUEUED("QUEUED"),

    SCHEDULED("SCHEDULED"),

    INPROGRESS("INPROGRESS"),

    COMPLETED("COMPLETED"),

    CANCELED("CANCELED"),

    FAILED("FAILED");

    private String value;

    JobStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String toString() {
        return String.valueOf(value);
    }

    public boolean isActiveState() {
        switch (this) {
            case SCHEDULED:
            case INPROGRESS:
                return true;
            default:
                return false;
        }
    }

    public boolean isTerminalState() {
        switch (this) {
            case COMPLETED:
            case CANCELED:
            case FAILED:
                return true;
            default:
                return false;
        }
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public boolean isFailed() {
        return this == FAILED;
    }

    public static boolean isTerminalState(Status status) {
        switch (status) {
            case COMPLETED:
            case CANCELED:
            case FAILED:
                return true;
            default:
                return false;
        }
    }

    public static boolean isCompleted(Status status) {
        return status == Status.COMPLETED;
    }

}
