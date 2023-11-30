package com.autodesk.compute.common;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.autodesk.compute.model.PathData;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.net.URI;
import java.util.function.Predicate;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestRequestTraceFilter {
    private RequestTraceFilter unfilteredRequestTraceFilter;

    private RequestTraceFilter mockFilteredRequestTraceFilter;

    @Mock
    private ContainerRequestContext requestContext;

    @Mock
    private ContainerResponseContext responseContext;

    @Mock
    private Predicate<PathData> mockRequestFilter;

    @Captor
    private ArgumentCaptor<String> logging;

    @Mock
    private UriInfo uriInfo;

    @Before
    public void init() {
        unfilteredRequestTraceFilter = spy(new RequestTraceFilter());
        MockitoAnnotations.openMocks(this);
        mockFilteredRequestTraceFilter = spy(new RequestTraceFilter(mockRequestFilter));
        Mockito.doReturn(uriInfo).when(requestContext).getUriInfo();
        doNothing().when(unfilteredRequestTraceFilter).writeDebugLog(logging.capture());
    }

    @Test
    public void filterAll() throws IOException {
        Mockito.doReturn(false).when(mockRequestFilter).test(any(PathData.class));
        Mockito.doReturn("/a-path").when(uriInfo).getPath();
        mockFilteredRequestTraceFilter.filter(requestContext);
        verify(mockRequestFilter, times(1)).test(any(PathData.class));
        verify(mockFilteredRequestTraceFilter, times(0)).writeDebugLog(anyString());
    }

    @Test
    public void logSomething() throws IOException {
        Mockito.doReturn("/a-path").when(uriInfo).getPath();
        Mockito.doReturn(URI.create("/a-path")).when(uriInfo).getAbsolutePath();
        Mockito.doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        Mockito.doReturn(new MultivaluedHashMap<>()).when(requestContext).getHeaders();
        final ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);
        unfilteredRequestTraceFilter.filter(requestContext);
        assertEquals(1, logging.getAllValues().size());
        final String capturedLog = logging.getAllValues().get(0);
        assertTrue(capturedLog.startsWith("URL:"));
    }

    @Test
    public void logHeaderSecretElided() throws IOException {
        // do
        final MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("x-ads-token-data", "my-secret-here");
        headers.add("not-a-secret", "not-a-secret2");
        Mockito.doReturn("/a-path").when(uriInfo).getPath();
        Mockito.doReturn(URI.create("/a-path")).when(uriInfo).getAbsolutePath();
        Mockito.doReturn(new MultivaluedHashMap<>()).when(uriInfo).getQueryParameters();
        Mockito.doReturn(headers).when(requestContext).getHeaders();

        // when
        unfilteredRequestTraceFilter.filter(requestContext);

        // then
        assertEquals(1, logging.getAllValues().size());
        final String capturedLog = logging.getAllValues().get(0);
        assertTrue(capturedLog.startsWith("URL:"));
        assertFalse(capturedLog.contains("my-secret-here"));
        assertTrue(capturedLog.contains("not-a-secret2"));
    }

    @Test
    public void logQueryParameterSecretElided() throws IOException {
        // do
        final MultivaluedHashMap<String, String> queryParameters = new MultivaluedHashMap<>();
        queryParameters.add("jobSecret", "my-secret-here");
        queryParameters.add("not-a-secret", "not-a-secret2");
        Mockito.doReturn("/a-path").when(uriInfo).getPath();
        Mockito.doReturn(URI.create("/a-path")).when(uriInfo).getAbsolutePath();
        Mockito.doReturn(queryParameters).when(uriInfo).getQueryParameters();
        Mockito.doReturn(new MultivaluedHashMap<>()).when(requestContext).getHeaders();

        // when
        unfilteredRequestTraceFilter.filter(requestContext);

        // then
        assertEquals(1, logging.getAllValues().size());
        final String capturedLog = logging.getAllValues().get(0);
        assertTrue(capturedLog.startsWith("URL:"));
        assertFalse(capturedLog.contains("my-secret-here"));
        assertTrue(capturedLog.contains("not-a-secret2"));
    }

}
