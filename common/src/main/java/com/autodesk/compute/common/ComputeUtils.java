package com.autodesk.compute.common;

import com.autodesk.compute.logging.MDCFields;
import com.pivovarit.function.ThrowingSupplier;
import com.pivovarit.function.exception.WrappedException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.SecurityContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntFunction;
import java.util.function.Supplier;

@Slf4j
public class ComputeUtils {

    private static final String API_CALL_WITH_RETRY = "apiCallWithRetry: ";

    // To work around a gatekeeper issue, some of the APIs need to return quickly -
    // in particular, the call to get a single, portfolio-version-stamped ADF.
    // The POST /jobs/ API call goes through Apigee, and therefore has to return within
    // 10 seconds. So our code basically gets one chance, with no retries, for that case.
    public enum TimeoutSettings {
        FAST,
        SLOW
    }

    public enum PrepareForRetryResponse {
        RETRY_OK,
        DONT_BOTHER_RETRYING,
        RETRY_NOT_NEEDED
    }

    private static final Random random = new Random();

    @SneakyThrows(InterruptedException.class)
    public static Optional<HttpResponse<String>> apiCallWithRetry(final Supplier<HttpResponse<String>> apiCall,
                                                                  final IntFunction<PrepareForRetryResponse> callBeforeRetry,
                                                                  final TimeoutSettings settings, final String description) {

        // Slow settings: 3; Fast settings: 1
        final int maxRetryCount = settings == TimeoutSettings.FAST ? 1 : 3;

        int currentRetry = 0;

        Optional<HttpResponse<String>> result = Optional.empty();
        do {
            PrepareForRetryResponse retryResponse = PrepareForRetryResponse.RETRY_NOT_NEEDED;
            try {
                result = Optional.ofNullable(apiCall.get());
                retryResponse = getPrepareForRetryResponse(callBeforeRetry, result, retryResponse, result.map(HttpResponse<String>::statusCode));
            } catch (final ProcessingException exception) {
                log.warn(API_CALL_WITH_RETRY, exception);
                retryResponse = PrepareForRetryResponse.RETRY_OK;
            } catch (final WrappedException e) {
                log.error(API_CALL_WITH_RETRY + description, e);
                return result;
            } catch (final Exception e) {
                log.error(API_CALL_WITH_RETRY + description, e);
                return result;
            }

            if (retryResponse == PrepareForRetryResponse.DONT_BOTHER_RETRYING ||
                    retryResponse == PrepareForRetryResponse.RETRY_NOT_NEEDED)
                return result;

            currentRetry += 1;
            // Sleep before a retry; bail quickly on the final retry
            if (currentRetry < maxRetryCount)
                Thread.sleep(750L * (1 << currentRetry) + random.nextInt(250));
        } while (currentRetry < maxRetryCount);
        return result;
    }

    /**
     * Simple wrapper for executing a pre-built rest request
     * - this creates a java.net.HttpClient, performs the request on a single thread, then cleans up the thread
     * - Java net client is not Closeable, so we can't use try-with-resources to close/shutdown
     *
     * @param request
     * @return
     * @throws IOException
     */
    @SneakyThrows(InterruptedException.class)
    public static HttpResponse<String> executeRequestForJavaNet(final HttpRequest request, final TimeoutSettings settings) throws IOException {
        HttpResponse<String> response = null;
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final int connectTimeout = (settings == ComputeUtils.TimeoutSettings.FAST) ? 2 : 5;
        try {
            final HttpClient client = HttpClient.newBuilder().
                    connectTimeout(Duration.of(connectTimeout, ChronoUnit.SECONDS)).
                    executor(executor).
                    build();
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } finally {
            // Java net client is not Closeable, so we can't use try-with-resources to close/shutdown
            executor.shutdownNow();
        }
        return response;
    }

    private static PrepareForRetryResponse getPrepareForRetryResponse(final IntFunction<PrepareForRetryResponse> callBeforeRetry,
                                                                      final Optional<HttpResponse<String>> result,
                                                                      PrepareForRetryResponse retryResponse,
                                                                      final Optional<Integer> statusCode) {
        if (!result.isPresent()) {
            retryResponse = PrepareForRetryResponse.RETRY_OK;
        } else if (statusCode
                .filter(status -> status != 200)
                .isPresent()) {
            retryResponse = callBeforeRetry.apply(result.get().statusCode()); //NOSONAR
        }
        return retryResponse;
    }

    @SafeVarargs
    public static <T, E extends Exception> T firstSupplier(final ThrowingSupplier<T, E>... suppliers) throws E {
        for (final ThrowingSupplier<T, E> oneSupplier : suppliers) {
            final T result = oneSupplier.get();
            if (result != null)
                return result;
        }
        return null;
    }

    public static String guessMonikerMDCfromSecurityContext(final SecurityContext securityContext) {
        return Optional.ofNullable(securityContext)
                .map(SecurityContext::getUserPrincipal)
                .map(Principal::getName)
                .orElse(MDCFields.UNSPECIFIED);
    }
}
