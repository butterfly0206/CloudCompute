package com.autodesk.compute.exception;

import com.autodesk.compute.common.model.Error;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.google.common.base.Throwables;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Provider
public class NoClassDefFoundErrorExceptionMapper implements ExceptionMapper<NoClassDefFoundError> {
    @Override
    public Response toResponse(final NoClassDefFoundError noClassDefFoundError) {
        log.error("NoClassDefFoundError exceptions are treated as FATAL - self-destructing in two seconds", noClassDefFoundError);
        final ComputeErrorCodes code = ComputeErrorCodes.SERVER_UNEXPECTED;
        final String stackTrace = StringEscapeUtils.escapeJson(Throwables.getStackTraceAsString(noClassDefFoundError));
        terminateAppInTwoSeconds();
        final Error responseEntity = new Error();
        responseEntity.setMessage(noClassDefFoundError.getMessage());
        responseEntity.setDescription(code.getDescription());
        responseEntity.setCode(code.name());
        responseEntity.setStackTrace(stackTrace);
        return Response.status(code.getStatus())
                .entity(responseEntity)
                .type("application/json")
                .build();
    }

    // This is here so it can be mocked in the tests
    public void terminateAppInTwoSeconds() {
        Executors.newSingleThreadScheduledExecutor().schedule(() -> System.exit(-1), 2, TimeUnit.SECONDS);
    }
}
