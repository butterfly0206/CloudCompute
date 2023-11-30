package com.autodesk.compute.common;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

@Slf4j
@UtilityClass
public class ThrottleCheck {    //NOPMD

    public static final String[] THROTTLING_STRINGS = new String[]{        //NOSONAR - unit tests need this public
            "Throughput exceeds the current capacity",
            "ThrottlingException",
            "TooManyRequestsException"
    };

    public static final String[] JOB_DEFINITION_STRINGS = new String[]{    //NOSONAR - unit tests
            "JobDefinition name",
            "does not exist or is not in ACTIVE status"
    };

    public static boolean isThrottlingException(final Exception e) {
        if (e == null || ComputeStringOps.isNullOrEmpty(e.getMessage()))
            return false;
        final String message = e.getMessage();
        final String exceptionString = e.toString();
        return Arrays.stream(THROTTLING_STRINGS).anyMatch(str -> message.contains(str) || exceptionString.contains(str));
    }

    public static boolean isJobDefinitionDoesNotExistException(final Throwable t) {
        if (t == null || ComputeStringOps.isNullOrEmpty(t.getMessage()))
            return false;
        final String message = t.getMessage();
        return Arrays.stream(JOB_DEFINITION_STRINGS).anyMatch(message::contains);
    }
}
