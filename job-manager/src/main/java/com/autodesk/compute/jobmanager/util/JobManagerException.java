package com.autodesk.compute.jobmanager.util;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.exception.IComputeException;
import com.autodesk.compute.jobmanager.api.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper=false)
public class JobManagerException extends NotFoundException implements IComputeException {
    private static final long serialVersionUID = 77;
    private final ComputeErrorCodes code;
    private final JsonNode details;

    public JobManagerException(final ComputeException e) {
        super(e.getCode().getStatus().getStatusCode(), e.getMessage());
        this.initCause(e);
        this.details = e.getDetails();
        this.code = e.getCode();
    }

    public JobManagerException(final ComputeErrorCodes code, final String message, final JsonNode details) {
        super(code.getStatus().getStatusCode(), message);
        this.code = code;
        this.details = makeDetails(details);
    }

    public JobManagerException(final ComputeErrorCodes code, final String message) {
        this(code, message, (JsonNode) null);
    }

    public JobManagerException(final ComputeErrorCodes code, final Throwable cause) {
        this(code, cause, null);
    }

    public JobManagerException(final ComputeErrorCodes code, final Throwable cause, final JsonNode details) {
        super(code.getStatus().getStatusCode(), cause.getMessage());
        this.initCause(cause);
        this.code = code;
        this.details = makeDetails(details);
    }

    public JobManagerException(final ComputeErrorCodes code, final String message, final Throwable cause) {
        this(code, message, cause, null);
    }

    @Builder
    public JobManagerException(final ComputeErrorCodes code, final String message, final Throwable cause, final JsonNode details) {
        super(code.getStatus().getStatusCode(), message);
        this.initCause(cause);
        this.code = code;
        this.details = details;
    }

    public static void wrapAndRethrow(final Exception e) throws JobManagerException {
        if (e instanceof JobManagerException)
            throw (JobManagerException) e;
        else if (e instanceof ComputeException)
            throw new JobManagerException((ComputeException) e);
        else
            throw new JobManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, e);
    }

}
