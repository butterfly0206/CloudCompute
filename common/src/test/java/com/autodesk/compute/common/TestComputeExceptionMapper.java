package com.autodesk.compute.common;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.exception.ComputeExceptionMapper;
import com.autodesk.compute.exception.NoClassDefFoundErrorExceptionMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.net.URISyntaxException;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TestComputeExceptionMapper {

    private MockHttpRequest mockRequest;

    @Spy
    private ComputeExceptionMapper mapper;

    @Spy
    private NoClassDefFoundErrorExceptionMapper noClassDefFoundErrorExceptionMapper;

    @Before
    public void initialize() throws URISyntaxException {
        MockitoAnnotations.openMocks(this);
        mockRequest = MockHttpRequest.create("GET", "https://localhost/api/v1/hello?jobSecret=notReally&other=okay");
        mockRequest.contentType("application/json");
        when(mapper.getRequest()).thenReturn(mockRequest);
        doNothing().when(noClassDefFoundErrorExceptionMapper).terminateAppInTwoSeconds();
    }

    @Test
    public void testIsStagingOrProduction()
    {
        assertFalse(mapper.isStagingOrProduction("cosv2-c-uw2"));
        assertTrue(mapper.isStagingOrProduction("fpccomp-s-ue1-dn"));
        assertTrue(mapper.isStagingOrProduction("cosv2-p-ue1-ds"));
        assertFalse(mapper.isStagingOrProduction(null));
    }

    @Test
    public void testVariousExceptions()
    {
        Response response = mapper.toResponse(new IllegalArgumentException("noise"));
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        response = mapper.toResponse(new NotAuthorizedException("go away"));
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        response = mapper.toResponse(new NotAllowedException("Try a different HTTP verb"));
        assertEquals(Response.Status.METHOD_NOT_ALLOWED.getStatusCode(), response.getStatus());
        response = mapper.toResponse(new NotFoundException("Job? What job?"));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        response = mapper.toResponse(new ComputeResteasyJackson2Provider.InputValidationException(
                "I'm sorry Dave, I can't let you do that"));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        response = mapper.toResponse(new ComputeException(ComputeErrorCodes.AWS_INTERNAL_ERROR, "AWS Failed? Shocking!"));
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        response = mapper.toResponse(new NotAllowedException(
                new ComputeException(ComputeErrorCodes.NOT_AUTHORIZED, "Nested in a silly way")));
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        response = mapper.toResponse(MismatchedInputException.from(null, String.class, "Json schema for data failed"));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        response = mapper.toResponse(new JsonMappingException(null, "Json mapping exception for data"));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        response = mapper.toResponse(new NotSupportedException("Protocol is not supported"));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());

        response = noClassDefFoundErrorExceptionMapper.toResponse(new NoClassDefFoundError());
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void testHandlingExceptionsInIsStagingOrProduction() {
        when(mapper.isStagingOrProduction(anyString())).thenThrow(new NullPointerException("Don't do that"));
        final Response response = mapper.toResponse(new NotFoundException("Job? What job?"));
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        verify(mapper, times(1)).isStagingOrProduction(anyString());
    }

    @Test
    public void testFilteringOfQueryParameters() {
        // Dig into the response and make sure that "notReally" from the mock request created above is not there.
        final Response response = mapper.toResponse(new IllegalArgumentException("noise"));
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        final com.autodesk.compute.common.model.Error error = (com.autodesk.compute.common.model.Error) response.getEntity();
        final JsonNode requestContext = (JsonNode) error.getRequestContext();
        final JsonNode queryParameters = requestContext.get("queryParameters");
        assertNotNull(queryParameters.get("jobSecret"));
        assertNotEquals("notReally", queryParameters.get("jobSecret").get(0).asText());
        assertEquals("okay", queryParameters.get("other").get(0).asText());
    }
}
