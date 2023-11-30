package com.autodesk.compute.common;

import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.categories.UnitTests;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;
import org.testng.Assert;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.autodesk.compute.test.TestHelper.makeAnswer;
import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Slf4j
@Category(UnitTests.class)
public class TestOxygenClient {

    private static final String OXYGEN_TOKEN = "OXYGEN TOKEN";
    private static final String OLD_OXYGEN_TOKEN = "OLD_OXYGEN_TOKEN";

    @Mock
    private final HttpResponse<String> responseGood = Mockito.mock(HttpResponse.class);
    @Mock
    private final HttpResponse<String> responseBad = Mockito.mock(HttpResponse.class);

    @Spy
    private final OxygenClient oxygenClient = Mockito.spy(
            new OxygenClient(
                    ComputeConfig.getInstance().getOxygenUrl(),
                    ComputeConfig.getInstance().getOxygenClientId(),
                    ComputeConfig.getInstance().getOxygenClientSecret(),
                    ComputeUtils.TimeoutSettings.SLOW));

    @Before
    public void init() {
        MockitoAnnotations.openMocks(this);
        initGoodResponse();
    }

    private void initGoodResponse() {
        final String message = ComputeStringOps.makeString("{ \"", OxygenClient.ACCESS_TOKEN, "\" : \"", OXYGEN_TOKEN, "\" }");
        when(responseGood.body()).thenReturn(message);
        when(responseGood.version()).thenReturn(HttpClient.Version.HTTP_1_1);
        when(responseGood.statusCode()).thenReturn(HttpStatus.SC_OK);
    }

    private void initBadResponse(final int httpStatus) {
        final String message = "{\"got\" : \"error\"}";
        when(responseBad.body()).thenReturn(message);
        when(responseBad.version()).thenReturn(HttpClient.Version.HTTP_1_1);
        when(responseBad.statusCode()).thenReturn(httpStatus);
    }

    @Test
    public void testGetNewCloudOSComputeTokenOK() throws IOException, ComputeException {
        Mockito.doReturn(responseGood).when(oxygenClient).executeRequest(any(HttpRequest.class));
        final String oxygenToken = oxygenClient.getNewCloudOSComputeToken();
        Assert.assertEquals(OXYGEN_TOKEN, oxygenToken);
    }

    @Test
    public void testGetNewCloudOSComputeTokenWithRetryOK() throws IOException, ComputeException {
        initBadResponse(HttpStatus.SC_BAD_GATEWAY);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseBad, responseGood);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        final String oxygenToken = oxygenClient.getNewCloudOSComputeToken();
        Assert.assertEquals(OXYGEN_TOKEN, oxygenToken);
    }

    @Test
    public void testGetNewCloudOSComputeTokenException400() throws IOException {
        // Test with 3 BAD_REQUESTs
        initBadResponse(HttpStatus.SC_BAD_REQUEST);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseBad, responseBad);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_ERROR),
                () -> oxygenClient.getNewCloudOSComputeToken()
        );
    }

    @Test
    public void testGetNewCloudOSComputeTokenException502() throws IOException {
        // Test with 3 BAD_GATEWAY responses
        initBadResponse(HttpStatus.SC_BAD_GATEWAY);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseBad, responseBad);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_ERROR_502),
                () -> oxygenClient.getNewCloudOSComputeToken()
        );
    }

    @Test
    public void testGetNewCloudOSComputeTokenException503() throws IOException {
        // Test with 3 SERVICE_UNAVAILABLE responses
        initBadResponse(HttpStatus.SC_SERVICE_UNAVAILABLE);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseBad, responseBad);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_ERROR_503),
                () -> oxygenClient.getNewCloudOSComputeToken()
        );
    }

    @Test
    public void testGetNewCloudOSComputeTokenException504() throws IOException {
        // Test with 3 GATEWAY_TIMEOUT responses
        initBadResponse(HttpStatus.SC_GATEWAY_TIMEOUT);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseBad, responseBad);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_ERROR_504),
                () -> oxygenClient.getNewCloudOSComputeToken()
        );
    }

    @Test
    public void testExtendTokenOK() throws IOException, ComputeException {
        Mockito.doReturn(responseGood).when(oxygenClient).executeRequest(any(HttpRequest.class));
        final String oxygenToken = oxygenClient.extendToken(OLD_OXYGEN_TOKEN);
        Assert.assertEquals(OXYGEN_TOKEN, oxygenToken);
    }

    @Test
    public void testExtendTokenWithRetryOK() throws IOException, ComputeException {
        initBadResponse(HttpStatus.SC_BAD_GATEWAY);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseBad, responseGood);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        final String oxygenToken = oxygenClient.extendToken(OLD_OXYGEN_TOKEN);
        Assert.assertEquals(OXYGEN_TOKEN, oxygenToken);
    }

    @Test
    public void testExtendTokenMissingTokenException() throws IOException {
        // Test with empty token
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_MISSING_TOKEN_ERROR),
                () -> oxygenClient.extendToken("")
        );

    }

    @Test
    public void testExtendTokenException400() throws IOException {
        // Test with BAD_REQUEST
        initBadResponse(HttpStatus.SC_BAD_REQUEST);
        final Answer<HttpResponse> answers = makeAnswer(responseBad);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_ERROR),
                () -> oxygenClient.extendToken(OLD_OXYGEN_TOKEN)
        );
    }

    @Test
    public void testExtendTokenException401RetryOK() throws ComputeException, IOException {
        // Test with UNAUTHORIZED, then good response
        initBadResponse(HttpStatus.SC_UNAUTHORIZED);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseGood, responseGood);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        oxygenClient.extendToken(OLD_OXYGEN_TOKEN);
    }

    @Test
    public void testExtendTokenException500() throws IOException {
        // Test with 3 BAD_GATEWAY responses
        initBadResponse(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseBad, responseBad);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_ERROR),
                () -> oxygenClient.extendToken(OLD_OXYGEN_TOKEN)
        );
    }

    @Test
    public void testExtendTokenException502() throws IOException {
        // Test with 3 BAD_GATEWAY responses
        initBadResponse(HttpStatus.SC_BAD_GATEWAY);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseBad, responseBad);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_ERROR_502),
                () -> oxygenClient.extendToken(OLD_OXYGEN_TOKEN)
        );
    }

    @Test
    public void testExtendTokenException503() throws IOException {
        // Test with 3 SERVICE_UNAVAILABLE responses
        initBadResponse(HttpStatus.SC_SERVICE_UNAVAILABLE);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseBad, responseBad);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_ERROR_503),
                () -> oxygenClient.extendToken(OLD_OXYGEN_TOKEN)
        );
    }

    @Test
    public void testExtendTokenException504() throws IOException {
        // Test with 3 GATEWAY_TIMEOUT responses
        initBadResponse(HttpStatus.SC_GATEWAY_TIMEOUT);
        final Answer<HttpResponse> answers = makeAnswer(responseBad, responseBad, responseBad);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_ERROR_504),
                () -> oxygenClient.extendToken(OLD_OXYGEN_TOKEN)
        );
    }

    @Test
    public void testRequestInterrupted() throws IOException {
        // Covering connection interruptions - we can't get to Oxygen
        Mockito.doAnswer(invocation -> {
            throw new InterruptedException("from test");
        }).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_UNAVAILABLE),
                () -> oxygenClient.extendToken(OLD_OXYGEN_TOKEN)
        );
    }

    @Test
    public void testRequestIOException() throws IOException {
        // Covering connection interruptions - we can't get to Oxygen
        Mockito.doAnswer(invocation -> {
            throw new IOException("from test");
        }).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_UNAVAILABLE),
                () -> oxygenClient.extendToken(OLD_OXYGEN_TOKEN)
        );
    }

    @Test
    public void testExtractTokenFromEmptyResponse() throws IOException {
        final Answer<HttpResponse> answers = makeAnswer(null, null, null);
        Mockito.doAnswer(answers).when(oxygenClient).executeRequest(any(HttpRequest.class));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.OXYGEN_UNAVAILABLE),
                () -> oxygenClient.extendToken(OLD_OXYGEN_TOKEN)
        );
    }

}
