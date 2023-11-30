package com.autodesk.compute.configuration;

import jakarta.ws.rs.core.Response;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ComputeErrorCodes {
    BAD_REQUEST("Bad request", Response.Status.BAD_REQUEST),
    NOT_FOUND("Resource not found", Response.Status.NOT_FOUND),
    INVALID_JOB_PAYLOAD("Invalid job payload", Response.Status.BAD_REQUEST),
    INVALID_SERVICE_NAME("Invalid service name", Response.Status.BAD_REQUEST),
    INVALID_WORKER_NAME("Invalid worker name", Response.Status.BAD_REQUEST),
    INVALID_PORTFOLIO_VERSION("Invalid portfolio version", Response.Status.BAD_REQUEST),
    INVALID_JOB_RESULT("Invalid job result", Response.Status.BAD_REQUEST),
    JOB_TIMED_OUT("Job timed out", Response.Status.UNAUTHORIZED),
    JOB_PROGRESS_UPDATE_ERROR("Failed to update job progress", Response.Status.INTERNAL_SERVER_ERROR),
    JOB_NOT_FOUND("Job not found", Response.Status.NOT_FOUND),
    IDEMPOTENT_JOB_EXISTS("Job with same idempotencyId already exists", Response.Status.CONFLICT),
    SERVER_BUSY("Server busy, try again later...", Response.Status.INTERNAL_SERVER_ERROR),
    NOT_AUTHORIZED("Request not authorized", Response.Status.UNAUTHORIZED),
    NOT_ALLOWED("Request not allowed", Response.Status.METHOD_NOT_ALLOWED),
    CONFIGURATION("Bad configuration", Response.Status.INTERNAL_SERVER_ERROR),
    SERVICE_DISCOVERY_EXCEPTION("Resource not found", Response.Status.INTERNAL_SERVER_ERROR),
    RESOURCE_CREATION_ERROR("Resource could not be created.", Response.Status.INTERNAL_SERVER_ERROR),
    SERVER("Internal Server Exception", Response.Status.INTERNAL_SERVER_ERROR),
    SERVER_UNEXPECTED("Unexpected Internal Server Exception", Response.Status.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE("Service Unavailable", Response.Status.SERVICE_UNAVAILABLE),
    AWS_BAD_REQUEST("Invalid request to AWS", Response.Status.BAD_REQUEST),
    AWS_CONFLICT("Conflict value in AWS", Response.Status.CONFLICT),
    DYNAMODB_TRANSACTION_CONFLICT("Conflict due to ongoing DynamoDB transaction", Response.Status.CONFLICT),
    AWS_INTERNAL_ERROR("AWS internal error", Response.Status.INTERNAL_SERVER_ERROR),
    VAULT_ERROR("Vault error", Response.Status.INTERNAL_SERVER_ERROR),
    OXYGEN_UNAVAILABLE("Unable to communicate with Oxygen", Response.Status.SERVICE_UNAVAILABLE),
    OXYGEN_ERROR(Constants.ERROR_GETTING_TOKEN_FROM_OXYGEN, Response.Status.INTERNAL_SERVER_ERROR),
    OXYGEN_ERROR_502(Constants.ERROR_GETTING_TOKEN_FROM_OXYGEN, Response.Status.BAD_GATEWAY),
    OXYGEN_ERROR_503(Constants.ERROR_GETTING_TOKEN_FROM_OXYGEN, Response.Status.BAD_GATEWAY),
    OXYGEN_ERROR_504(Constants.ERROR_GETTING_TOKEN_FROM_OXYGEN, Response.Status.GATEWAY_TIMEOUT),
    OXYGEN_EXTEND_EXPIRED_INVALID_TOKEN_ERROR("The token has expired or is invalid", Response.Status.UNAUTHORIZED),
    OXYGEN_MISSING_TOKEN_ERROR("Could not find token in the request.", Response.Status.UNAUTHORIZED),
    REQUEST_TIMED_OUT("The request timed out before producing a response.", Response.Status.REQUEST_TIMEOUT),
    JOB_TIMEOUT_FORBIDDEN("The job has timed out, so no additional progress or heartbeats are allowed", Response.Status.REQUEST_TIMEOUT),
    JOB_UPDATE_FORBIDDEN("The update on the job is not allowed", Response.Status.CONFLICT),
    NOT_IMPLEMENTED("API not implemented", Response.Status.NOT_IMPLEMENTED),
    TOO_MANY_REQUESTS("Too many requests", Response.Status.TOO_MANY_REQUESTS);

    private final String description;
    private final Response.Status status;

    private static class Constants {
        private static final String ERROR_GETTING_TOKEN_FROM_OXYGEN = "Error getting token from Oxygen";
    }
}
