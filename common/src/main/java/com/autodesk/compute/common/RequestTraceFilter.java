package com.autodesk.compute.common;

import com.autodesk.compute.model.PathData;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// The priorities are ordered. The intention is to have the RequestTraceFilter run *after* the MDCRequestFilter.
@AllArgsConstructor
@Provider
@Slf4j
@Priority(10)
public class RequestTraceFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Set<String> dontLogTheseHeaderValues = Stream.of(
            "x-ads-token-data",
            "authorization",
            "x-ads-gateway-secret",
            "x-vault-token",
            "x-ads-token-data-b64"
    ).collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));

    private static final Set<String> dontLogTheseQueryParameters = Stream.of(
            "jobSecret"
    ).collect(Collectors.toSet());

    private final Predicate<PathData> logFilterCriterion;

    public RequestTraceFilter() {
        this((pathData) -> true);
    }

    // There's a useful logging method called logAndClone that was removed from this code.
    // It's used for logging the entire body of a request. It's constrained to requests less than something like 125K, so
    // it doesn't create a DOS problem. The code is in the gatekeeper also, at:
    // https://git.autodesk.com/cloud-platform/embrace-gatekeeper/blob/d969a59edc744a956bcb1b2eb356f2d5e313c1ae/common/src/main/java/com/autodesk/cloudos/embrace/utils/RequestTraceFilter.java#L80

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        if (!log.isDebugEnabled())
            return;

        final PathData pathData = new PathData(
                requestContext.getUriInfo().getPath(), requestContext.getMethod());
        if (!logFilterCriterion.test(pathData)) {
            return;
        }

        final List<String> headerStrings = requestContext.getHeaders().entrySet().stream().map(
                entry -> {
                    final String valueToLog = dontLogTheseHeaderValues.contains(entry.getKey()) ? "elided" : String.join(", ", entry.getValue());
                    return entry.getKey() + ": " + valueToLog;
                }
        ).collect(Collectors.toList());
        final String allHeaders = String.join(", ", headerStrings);

        // This makes a list of strings of: queryParameter:value1,value2,...,valueN while eliding the things that need to be elided.
        final List<String> queryParameterStrings = requestContext.getUriInfo().getQueryParameters().entrySet().stream().map(
                entry -> {
                    final String valueToLog = dontLogTheseQueryParameters.contains(entry.getKey()) ? "elided" : String.join(", ", entry.getValue());
                    return entry.getKey() + ": " + valueToLog;
                }
        ).collect(Collectors.toList());
        final String allQueryParameters = String.join(", ", queryParameterStrings);

        writeDebugLog("URL: " + requestContext.getUriInfo().getAbsolutePath() + " Query Parameters: " + allQueryParameters + " Headers: " + allHeaders);
    }

    // This is only here so it can be spied upon in tests
    public void writeDebugLog(final String str) {
        log.debug(str);
    }

    @Override
    public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext) throws IOException {
        // Do nothing in the logging filter
    }
}
