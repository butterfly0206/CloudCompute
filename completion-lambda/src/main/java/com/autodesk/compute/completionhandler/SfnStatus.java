package com.autodesk.compute.completionhandler;

public enum SfnStatus {

    RUNNING("RUNNING"),

    SUCCEEDED("SUCCEEDED"), //NOPMD

    FAILED("FAILED"), //NOPMD

    ABORTED("ABORTED"),

    TIMED_OUT("TIMED_OUT");

    private final String value;

    SfnStatus(final String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

}
