package com.autodesk.compute.common;

import com.amazonaws.util.StringUtils;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
public class OxygenClient {

    private static final String ERROR_GETTING_TOKEN_FROM_OXYGEN = "Error getting new token from oxygen: ";
    private static final String ERROR_EXTENDING_TOKEN_FROM_OXYGEN = "Error extending token from oxygen.";
    public static final String ACCESS_TOKEN = "access_token";

    private final String computeClientId;
    private final String computeClientSecret;
    private final String authTokenUrl;
    private final String extendTokenUrl;
    private final ComputeUtils.TimeoutSettings timeoutSettings;

    // Do not use this field directly, use synchronized getter/setter instead.
    private static String computeToken;

    private synchronized String getComputeToken() {
        return computeToken;
    }

    private synchronized void setComputeToken(final String token) {
        computeToken = token;
    }

    @Inject
    public OxygenClient() {
        this(ComputeConfig.getInstance().getOxygenUrl(), ComputeConfig.getInstance().getOxygenClientId(), ComputeConfig.getInstance().getOxygenClientSecret(), ComputeUtils.TimeoutSettings.SLOW);
    }

    public OxygenClient(final String oxygenUrl, final String oxygenClientId, final String oxygenSecret, final ComputeUtils.TimeoutSettings timeoutSettings) {
        this.computeClientId = oxygenClientId;
        this.computeClientSecret = oxygenSecret;
        this.timeoutSettings = timeoutSettings;
        String authUrl = oxygenUrl;
        if (!authUrl.endsWith("/")) {
            authUrl = authUrl + "/";
        }
        // https://wiki.autodesk.com/display/FIAMPA/Migrate+from+OAuth+2+V1+to+V2+endpoints+for+2L+tokens
        this.authTokenUrl = authUrl + "authentication/v2/token";
        // https://wiki.autodesk.com/display/FIAMPA/Migrate+from+OAuth+2+V1+to+V2+Add+Claims+and+Extend+Token+endpoints
        this.extendTokenUrl = authUrl + "authentication/v2/extendtoken";
    }

    // This is here for testing/mocking
    public HttpResponse<String> executeRequest(final HttpRequest request) throws IOException {
        return ComputeUtils.executeRequestForJavaNet(request, this.timeoutSettings);
    }

    private HttpResponse<String> post(final String url, final Map<String, String> formParameters, final Map<String, String> headers)
            throws UnsupportedEncodingException, IOException {

        final List<NameValuePair> pairs = new ArrayList<>();
        for (final Map.Entry<String, String> entry : formParameters.entrySet()) {
            pairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }

        final InputStream inputStream = new UrlEncodedFormEntity(pairs).getContent();
        String content;
        try {
            final StringWriter writer = new StringWriter();
            IOUtils.copy(inputStream, writer);
            content = writer.toString();
        } finally {
            inputStream.close();
        }

        final int readTimeout = (timeoutSettings == ComputeUtils.TimeoutSettings.FAST) ? 2 : 5;
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.of(readTimeout, ChronoUnit.SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(content));

        for (final Map.Entry<String, String> entry : headers.entrySet()) {
            builder.setHeader(entry.getKey(), entry.getValue());
        }

        return executeRequest(builder.build());
    }

    private HttpResponse<String> post(final String url, final String requestBody, final Map<String, String> headers)
            throws IOException {

        final HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        for (final Map.Entry<String, String> entry : headers.entrySet()) {
            builder.setHeader(entry.getKey(), entry.getValue());
        }

        return executeRequest(builder.build());

    }

    public String getNewCloudOSComputeToken() throws ComputeException {

        final String tokenScope = "data:read";
        // Switching to v2 of auth api as Apigee-X does not seem to support v1
        // https://wiki.autodesk.com/display/FIAMPA/Migrate+from+OAuth+2+V1+to+V2+endpoints+for+2L+tokens
        final String auth = ComputeStringOps.makeString(computeClientId, ":", computeClientSecret);
        String base64Auth = Base64.getEncoder().encodeToString(auth.getBytes());

        final Map<String, String> headers = new HashMap<>();  //NOPMD
        headers.put("content-type", "application/x-www-form-urlencoded");
        headers.put("Authorization", ComputeStringOps.makeString("Basic ", base64Auth));

        final Map<String, String> formParams = new HashMap<>();   //NOPMD
        formParams.put("grant_type", "client_credentials");
        formParams.put("scope", tokenScope);

        final Optional<HttpResponse<String>> response = ComputeUtils.apiCallWithRetry(() -> {
                    try {
                        return post(authTokenUrl, formParams, headers);
                    } catch (final Exception e) {
                        log.warn("Unable to get new oxygen token", e);
                        return null;
                    }
                }, this::getNewCloudOSComputeTokenPrepareForRetry, timeoutSettings,
                "getNewCloudOSComputeTokenPrepareForRetry");

        return extractToken(response);

    }

    private ComputeUtils.PrepareForRetryResponse getNewCloudOSComputeTokenPrepareForRetry(final int httpStatusCode) {
        // If 400 is returned, something is very wrong, and we can't retry anymore
        // In any other case we should keep retrying
        if (httpStatusCode == 400) {
            return ComputeUtils.PrepareForRetryResponse.DONT_BOTHER_RETRYING;
        } else {
            return ComputeUtils.PrepareForRetryResponse.RETRY_OK;
        }
    }

    @SneakyThrows(JsonProcessingException.class)
    public String extendToken(final String tokenToExtend) throws ComputeException {

        if (StringUtils.isNullOrEmpty(tokenToExtend)) {
            log.error("Token is not provided in the request.");
            throw new ComputeException(ComputeErrorCodes.OXYGEN_MISSING_TOKEN_ERROR, "missing token from request.");
        }

        final Map<String, String> headers = new HashMap<>();  //NOPMD
        headers.put("Content-Type", "application/json");

        final Map<String, String> requestBody = new HashMap<>();   //NOPMD
        requestBody.put("token", tokenToExtend);

        final String postBody = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(requestBody);
        final Optional<HttpResponse<String>> response = ComputeUtils.apiCallWithRetry(() -> {
            try {
                headers.put("Authorization", String.format("Bearer %s", getComputeToken()));
                return post(extendTokenUrl, postBody, headers);
            } catch (final Exception e) {
                log.warn("Unable to extend oxygen token", e);
                return null;
            }
        }, this::extendTokenPrepareForRetry, timeoutSettings, "extendOxygenTokenAPICall");

        return extractToken(response);
    }

    private String extractToken(final Optional<HttpResponse<String>> response) throws ComputeException {

        if (response.isEmpty()) {
            throw new ComputeException(ComputeErrorCodes.OXYGEN_UNAVAILABLE, "Oxygen not responding");
        }

        try {
            final String responseBody = response.get().body();

            final int status = response.get().statusCode();
            switch (status) {
                case 200:
                    final Map<String, Object> responseMap = Json.mapper.readValue(responseBody, HashMap.class);
                    return (String) responseMap.get(ACCESS_TOKEN);
                case 500:
                    throw new ComputeException(ComputeErrorCodes.OXYGEN_ERROR, responseBody);
                case 502:
                    throw new ComputeException(ComputeErrorCodes.OXYGEN_ERROR_502, responseBody);
                case 503:
                    throw new ComputeException(ComputeErrorCodes.OXYGEN_ERROR_503, responseBody);
                case 504:
                    throw new ComputeException(ComputeErrorCodes.OXYGEN_ERROR_504, responseBody);
                default:
                    throw new ComputeException(ComputeErrorCodes.OXYGEN_ERROR, responseBody);
            }
        } catch (final IOException e) {
            throw new ComputeException(ComputeErrorCodes.OXYGEN_ERROR, e);
        }
    }

    private ComputeUtils.PrepareForRetryResponse extendTokenPrepareForRetry(final int httpStatusCode) {
        if (httpStatusCode == 401) {
            try {
                setComputeToken(getNewCloudOSComputeToken());
            } catch (final Exception e) {
                log.error("Unable to refresh compute oxygen token", e);
                return ComputeUtils.PrepareForRetryResponse.DONT_BOTHER_RETRYING;
            }
            return ComputeUtils.PrepareForRetryResponse.RETRY_OK;
        } else if (httpStatusCode == Response.Status.GATEWAY_TIMEOUT.getStatusCode() || httpStatusCode == Response.Status.BAD_GATEWAY.getStatusCode()) {
            return ComputeUtils.PrepareForRetryResponse.RETRY_OK;
        } else {
            return ComputeUtils.PrepareForRetryResponse.DONT_BOTHER_RETRYING;
        }
    }
    
}
