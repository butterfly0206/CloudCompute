package com.autodesk.compute.monitor;

import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.testlambda.RetryInterceptor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.IOException;

import static org.mockito.Mockito.*;

@Slf4j
@Category(UnitTests.class)
public class TestInterceptorChain {

    // Reference: https://android.googlesource.com/platform/external/okhttp/+/bad0a11146d43955d3f3b949aa277f0dd7cc3abb/okhttp-tests/src/test/java/com/squareup/okhttp/InterceptorTest.java
    public MockWebServer server;
    private final OkHttpClient client;

    @Spy
    private RetryInterceptor interceptor;

    @SneakyThrows({InterruptedException.class, IOException.class})
    public TestInterceptorChain() {
        MockitoAnnotations.openMocks(this);
        server = new MockWebServer();
        client = new OkHttpClient.Builder().addInterceptor(interceptor).build();
        doNothing().when(interceptor).doWaitSleep(anyInt());
        server.start();
    }

    @Test
    public void countRetries() throws IOException, InterruptedException
    {
        final Request request = new Request.Builder()
                .url(server.url("/"))
                .build();
        server.enqueue(new MockResponse().setResponseCode(502));
        server.enqueue(new MockResponse().setResponseCode(504));
        server.enqueue(new MockResponse().setResponseCode(200));
        client.newCall(request).execute();
        verify(interceptor, times(2)).doWaitSleep(anyInt());
    }

    @Test
    public void exhaustedRetries() throws IOException, InterruptedException
    {
        server.enqueue(new MockResponse().setResponseCode(502));
        server.enqueue(new MockResponse().setResponseCode(504));
        server.enqueue(new MockResponse().setResponseCode(502));
        server.enqueue(new MockResponse().setResponseCode(502));
        final Request request = new Request.Builder()
                .url(server.url("/"))
                .build();
        final Response response = client.newCall(request).execute();
        // Note: this makes a total of four attempts; three retries.
        verify(interceptor, times(3)).doWaitSleep(anyInt());
        Assert.assertEquals("Final response should be 502", 502, response.code());
    }

    @Test
    public void noRetries() throws IOException, InterruptedException
    {
        final Request request = new Request.Builder()
                .url(server.url("/"))
                .build();
        server.enqueue(new MockResponse().setResponseCode(200));
        client.newCall(request).execute();
        verify(interceptor, never()).doWaitSleep(anyInt());
    }

}
