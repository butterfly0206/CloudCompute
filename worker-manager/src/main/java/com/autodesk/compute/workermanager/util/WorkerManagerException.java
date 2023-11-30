package com.autodesk.compute.workermanager.util;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.exception.IComputeException;
import com.autodesk.compute.workermanager.api.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper=false)
public class WorkerManagerException extends NotFoundException implements IComputeException {
    private static final long serialVersionUID = 77;
    private final ComputeErrorCodes code;
    private final JsonNode details;

    public WorkerManagerException(final ComputeException e) {
        super(e.getCode().getStatus().getStatusCode(), e.getMessage());
        this.initCause(e);
        this.details = e.getDetails();
        this.code = e.getCode();
    }

    public WorkerManagerException(final ComputeErrorCodes code, final String message) {
        this(code, message, (JsonNode) null);
    }

    public WorkerManagerException(final ComputeErrorCodes code, final String message, final JsonNode details) {
        super(code.getStatus().getStatusCode(), message);
        this.code = code;
        this.details = makeDetails(details);
    }

    public WorkerManagerException(final ComputeErrorCodes code, final Throwable cause) {
        this(code, cause, null);
    }

    public WorkerManagerException(final ComputeErrorCodes code, final Throwable cause, final JsonNode details) {
        super(code.getStatus().getStatusCode(), cause.getMessage());
        this.initCause(cause);
        this.code = code;
        this.details = makeDetails(details);
    }

    public WorkerManagerException(final ComputeErrorCodes code, final String message, final Throwable cause) {
        this(code, message, cause, null);
    }

    @Builder
    public WorkerManagerException(final ComputeErrorCodes code, final String message, final Throwable cause, final JsonNode details) {
        super(code.getStatus().getStatusCode(), message);
        this.initCause(cause);
        this.code = code;
        this.details = makeDetails(details);
    }

}

