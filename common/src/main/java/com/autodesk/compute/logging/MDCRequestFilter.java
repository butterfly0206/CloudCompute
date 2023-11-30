package com.autodesk.compute.logging;

import com.autodesk.compute.common.ComputeUtils;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.AllArgsConstructor;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by wattd on 6/1/17.
 */
// The priorities are ordered. The intention is to have the RequestTraceFilter run *after* the MDCRequestFilter.
@AllArgsConstructor
@Provider
@Priority(5)
public class MDCRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);
    private static final Map<String, MDCLoader> requestIdToMDCLoader = new ConcurrentHashMap<>();
    private static final String REQUEST_ID_HEADER = "RequestId";

    private static String constructRequestId(final int sequence) {
        return System.getenv("HOSTNAME") + "_" + sequence;
    }

    @Override
    public void filter(final ContainerRequestContext requestContext) {
        final String requestId = constructRequestId(SEQUENCE.incrementAndGet());
        requestContext.getHeaders().add(REQUEST_ID_HEADER, requestId);

        final String moniker = ComputeUtils.guessMonikerMDCfromSecurityContext(requestContext.getSecurityContext());
        requestIdToMDCLoader.put(requestId, MDCLoader.forRequestId(requestId).withMoniker(moniker));
    }

    @Override
    public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext) {
        if (requestContext.getHeaders().containsKey(REQUEST_ID_HEADER)) {
            final String requestId = requestContext.getHeaders().get(REQUEST_ID_HEADER).get(0);
            responseContext.getHeaders().add(REQUEST_ID_HEADER, requestId);
            requestIdToMDCLoader.remove(requestId);
            MDC.clear();
        }
    }

}
