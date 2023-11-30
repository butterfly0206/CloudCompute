package com.autodesk.compute.configuration;

import com.autodesk.compute.exception.IComputeException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Value
@ToString(callSuper = true)
public class ComputeException extends Exception implements IComputeException {
    private static final long serialVersionUID = 75;
    private final ComputeErrorCodes code;
    private final JsonNode details;

    public ComputeException(final ComputeErrorCodes code, final String message) {
        super(message);
        this.code = code;
        this.details = getMDC();
    }

    public ComputeException(final ComputeErrorCodes code, final String message, final JsonNode details) {
        super(message);
        this.code = code;
        this.details = makeDetails(details);
    }

    public ComputeException(final ComputeErrorCodes code, final Throwable cause) {
        this(code, cause, null);
    }

    public ComputeException(final ComputeErrorCodes code, final Throwable cause, final JsonNode details) {
        super(cause.getMessage(), cause);
        this.code = code;
        this.details = makeDetails(details);
    }

    public ComputeException(final ComputeErrorCodes code, final String message, final Throwable cause) {
        this(code, message, cause, null);
    }

    @Builder
    public ComputeException(final ComputeErrorCodes code, final String message, final Throwable cause, final JsonNode details) {
        super(message, cause);
        this.code = code;
        this.details = makeDetails(details);
    }

}
