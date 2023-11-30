package com.autodesk.compute.workermanager.util;


import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import lombok.experimental.UtilityClass;

import java.util.Base64;

@UtilityClass
public final class TaskTokenUtils {
    public static String getJobSecretFromTaskToken(final String taskToken) throws ComputeException {
        final String jobSecret;
        try {
            final byte[] encodedBytes = Base64.getEncoder().encode(taskToken.getBytes());
            jobSecret = new String(encodedBytes, "UTF-8");
        } catch (final java.io.UnsupportedEncodingException e) {
            throw new ComputeException(ComputeErrorCodes.SERVER_UNEXPECTED, e);
        }
        return jobSecret;
    }

    public static String getTaskTokenFromJobSecret(final String jobSecret) throws ComputeException {
        String taskToken = null;
        try {
            final byte[] decodedBytes = Base64.getDecoder().decode(jobSecret.getBytes());
            taskToken = new String(decodedBytes, "UTF-8");
        } catch (final Exception e) {
            throw new ComputeException(ComputeErrorCodes.BAD_REQUEST, "Invalid job secret", e);
        }
        return taskToken;
    }
}
