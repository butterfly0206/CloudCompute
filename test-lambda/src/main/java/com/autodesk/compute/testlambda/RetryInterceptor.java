package com.autodesk.compute.testlambda;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

// ApiClient client internally uses OkHttpClient which allows to implement Interceptor that can be used to
// implement retry logic Ref: https://square.github.io/okhttp/interceptors/
// Ref: https://stackoverflow.com/questions/24562716/how-to-retry-http-requests-with-okhttp-retrofit
@Slf4j
public class RetryInterceptor implements Interceptor {

    private static final Random random = new Random();
    private final List<Integer> retryCodes = Arrays.asList(HttpStatus.SC_GATEWAY_TIMEOUT, HttpStatus.SC_BAD_GATEWAY);

    public void doWaitSleep(final int delayMilliseconds) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(delayMilliseconds);
    }

    @SneakyThrows(InterruptedException.class)
    @Override
    public Response intercept(@NonNull final Interceptor.Chain chain) throws IOException {
        final Request request = chain.request();

        // try the request
        Response response = chain.proceed(request);

        int tryCount = 0;
        final int baseDelayMiliseconds = 500;
        while (tryCount < 3 && retryCodes.contains(response.code())) {
            log.info(makeString("Http retry intercept: retrying request on response code ("
                    , response.code(), "), retry count :", tryCount));
            tryCount++;

            // Exponential delay with some randomness
            final int delay = (int) (1L << tryCount) * baseDelayMiliseconds + random.nextInt(baseDelayMiliseconds);

            doWaitSleep(delay);

            // retry the request
            response = chain.proceed(request);
        }
        // otherwise just pass the original response on
        return response;
    }
}

