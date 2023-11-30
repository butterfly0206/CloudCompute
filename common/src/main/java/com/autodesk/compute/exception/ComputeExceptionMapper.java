package com.autodesk.compute.exception;

import com.autodesk.compute.common.ComputeResteasyJackson2Provider;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.model.Error;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.RequestContextData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.google.common.base.MoreObjects.firstNonNull;


@Provider
@Slf4j
public class ComputeExceptionMapper implements ExceptionMapper<Exception> {
    private static final Set<String> dontLogTheseQueryParameters = Stream.of(
            "jobSecret"
    ).collect(Collectors.toSet());

    @Override
    @Produces("application/json")
    public Response toResponse(final Exception e) {

        final IComputeException ce;
        if (e instanceof IComputeException) {
            ce = (IComputeException) e;
        } else if (e.getCause() instanceof IComputeException) {
            ce = (IComputeException) e.getCause();
        } else if (e instanceof NotAuthorizedException) {
            ce = new ComputeException(ComputeErrorCodes.NOT_AUTHORIZED, e);
        } else if (e instanceof ComputeResteasyJackson2Provider.InputValidationException) {
            ce = new ComputeException(ComputeErrorCodes.BAD_REQUEST, e);
        } else if (e instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException) {
            ce = new ComputeException(ComputeErrorCodes.BAD_REQUEST, e);
        }  else if (e instanceof com.fasterxml.jackson.core.JsonProcessingException) {
            ce = new ComputeException(ComputeErrorCodes.BAD_REQUEST, e);
        } else if (e instanceof com.fasterxml.jackson.core.JsonParseException) {
            ce = new ComputeException(ComputeErrorCodes.BAD_REQUEST, e);
        } else if (e instanceof NotFoundException) {
            ce = new ComputeException(ComputeErrorCodes.NOT_FOUND, e);
        } else if (e instanceof NotAllowedException) {
            ce = new ComputeException(ComputeErrorCodes.NOT_ALLOWED, e);
        } else if (e instanceof NotSupportedException) {
            ce = new ComputeException(ComputeErrorCodes.BAD_REQUEST, e);
        } else {
            ce = new ComputeException(ComputeErrorCodes.SERVER_UNEXPECTED, e);
        }
        return toResponse(ce);
    }

    public boolean isStagingOrProduction(final String cloudosMoniker) {
        // Not defined moniker = not staging or prod (actually a config problem)
        if (ComputeStringOps.isNullOrEmpty(cloudosMoniker))
            return false;
        final String lowercaseMoniker = cloudosMoniker.toLowerCase();
        return lowercaseMoniker.contains("-s-") || lowercaseMoniker.contains("-p-");
    }
    private Response toResponse(final IComputeException exceptionInterface) {
        boolean isStagingOrProduction = false;
        final Throwable exception = exceptionInterface instanceof Exception ? (Exception)exceptionInterface : exceptionInterface.getCause();
        try {
            final ComputeConfig config = ComputeConfig.getInstance();
            isStagingOrProduction = isStagingOrProduction(config.getCloudosMoniker());
        } catch (final RuntimeException e) {
            // Do nothing but proceed
        }
        // Walk the cause of the exception. If the cause reduces to a ClassNotFoundException, then we had an error
        // initializing this application, and we need to exit ASAP.
        Throwable nestedException = exception;
        while (nestedException.getCause() != null)
            nestedException = nestedException.getCause();

        final ComputeErrorCodes code = exceptionInterface.getCode();
        final boolean suppressStackTrace = isStagingOrProduction || code.ordinal() < 500;
        final String stackTrace = StringEscapeUtils.escapeJson(Throwables.getStackTraceAsString(exception));
        final Error responseEntity = new Error();
        responseEntity.setMessage(exception.getMessage());
        responseEntity.setDescription(code.getDescription());
        responseEntity.setCode(code.name());
        responseEntity.setDetails(exceptionInterface.getDetails());
        responseEntity.setRequestContext(getRequestContext());
        responseEntity.setStackTrace(suppressStackTrace ? "suppressed" : stackTrace);
        final Response resp = Response.status(code.getStatus())
                .entity(responseEntity)
                .type("application/json")
                .build();

        final String message = makeString("Returning error response : HTTP ", resp.getStatus(), " with message ", responseEntity.getMessage());
        try (final MDCLoader loader = MDCLoader.forField("errorCode", responseEntity.getCode())) {
            loader.withField("errorDescription", responseEntity.getDescription());
            loader.withField("errorMessage", responseEntity.getMessage());
            loader.withField("errorDetails", firstNonNull(responseEntity.getDetails(), "'").toString());
            loader.withField("requestContext", firstNonNull(responseEntity.getRequestContext(), "'").toString());
            if (resp.getStatus() >= 500) {
                log.error(message, exception);
            } else if (resp.getStatus() < 401 || resp.getStatus() > 404) {
                // Exclude authorization failures and 404
                log.warn(message, exception);
            }
        }
        return resp;
    }

    // Extracted method so it can be mocked
    public HttpRequest getRequest() {
        return ResteasyProviderFactory.getInstance().getContextData(HttpRequest.class);
    }

    private boolean hasFormParameters(final HttpRequest request) {
        return ("POST".equals(request.getHttpMethod()) || "PUT".equals(request.getHttpMethod())) && request.getHttpHeaders().getMediaType().isCompatible(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
    }

    private JsonNode getRequestContext() {
        final HttpRequest request = getRequest();
        final RequestContextData contextData =
                new RequestContextData(
                        request.getHttpMethod(),
                        request.getUri().getAbsolutePath().toString(),
                        request.getHttpHeaders().getHeaderString("x-forwarded-for"),
                        filterQueryParameters(request.getUri().getQueryParameters()),
                        hasFormParameters(request) ? request.getFormParameters() : new MultivaluedHashMap<>());
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(contextData);
    }

    private MultivaluedMap<String, String> filterQueryParameters(final MultivaluedMap<String, String> queryParameters) {
        final MultivaluedHashMap<String, String> result = new MultivaluedHashMap<>();
        queryParameters.entrySet().forEach(
                entry -> {
                    if (dontLogTheseQueryParameters.contains(entry.getKey()))
                        result.put(entry.getKey(), Collections.singletonList("elided"));
                    else
                        result.put(entry.getKey(), entry.getValue());
                }
        );
        return result;
    }
}
