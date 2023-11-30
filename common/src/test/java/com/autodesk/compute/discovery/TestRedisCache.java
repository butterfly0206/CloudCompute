package com.autodesk.compute.discovery;

import com.autodesk.compute.common.ComputeUtils;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ServiceWithVersion;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TestRedisCache {
    @Mock
    private RedisClient client;

    @Mock
    private StatefulRedisConnection<String, String> connection;

    @Mock
    private Metrics metrics;

    private RedisCache<AppDefinition> cache;

    private FakeRedisCommands responses;

    private class MyJsonException extends JsonProcessingException {
        public MyJsonException() {
            super("from test");
        }
    }

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
        RedisURI redisUri = RedisURI.create("redis://localhost:6379");
        cache = spy(new RedisCache<>(AppDefinition.class, redisUri, "redisNamespace", metrics));
        doReturn(client).when(cache).makeRedisClient(any(RedisURI.class));
        doReturn(connection).when(client).connect();
        responses = new FakeRedisCommands();
        doReturn(responses).when(connection).sync();
    }

    @Test
    public void connectionExceptionsSLOW() {
        final AppDefinition simple = AppDefinition.builder().build();
        final String key = ServiceWithVersion.makeKey("simple", "1.0.0");
        // throw  exceptions for two connection attempts (which should retry once)
        Mockito.doThrow(new RedisConnectionException("from test")).
                doThrow(new IllegalStateException("from test")).
                doThrow(new NullPointerException("from test")).
                doReturn(connection).
                when(client).connect();
        cache.set(key, simple, 5, ComputeUtils.TimeoutSettings.SLOW);

        // We should fail to connect three times, succeed on the fourth
        // (connection.sync only gets called if we are connected)
        verify(client, times(4)).connect();
        verify(connection, times(1)).sync();
        // We should call ensureConnected twice, once for set, once for setString
        verify(cache, times(2)).ensureConnected(any(ComputeUtils.TimeoutSettings.class));
    }

    @Test
    public void connectionExceptionsFAST() {
        final AppDefinition simple = AppDefinition.builder().build();
        final String key = ServiceWithVersion.makeKey("simple", "1.0.0");
        // throw  exceptions for two connection attempts (which should retry once)
        Mockito.doThrow(new RedisConnectionException("from test")).
                doThrow(new IllegalStateException("from test")).
                doThrow(new NullPointerException("from test")).
                doReturn(connection).
                when(client).connect();
        cache.set(key, simple, 5, ComputeUtils.TimeoutSettings.FAST);

        // We should fail to connect three times, succeed on the fourth
        // (connection.sync only gets called if we are connected)
        verify(client, times(4)).connect();
        verify(connection, times(1)).sync();
        // We should call ensureConnected twice, once for set, once for setString
        verify(cache, times(2)).ensureConnected(any(ComputeUtils.TimeoutSettings.class));
    }

    @Test
    public void setAndGet() {
        final AppDefinition simple = AppDefinition.builder().build();
        final String key = ServiceWithVersion.makeKey("simple", "1.0.0");
        cache.set(key, simple, 5, ComputeUtils.TimeoutSettings.SLOW);
        final Pair<RedisCache.Present, Optional<AppDefinition>> returned = cache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
        final Optional<AppDefinition> returnedFromGet = cache.get(key, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.PRESENT, returned.getLeft());
        Assert.assertEquals(simple, returned.getRight().get());
        Assert.assertTrue(returnedFromGet.isPresent());
        Assert.assertEquals(simple, returnedFromGet.get());
    }

    @Test
    public void setRemoveAndGet() {
        final AppDefinition simple = AppDefinition.builder().build();
        final String key = ServiceWithVersion.makeKey("simple", "1.0.0");
        cache.set(key, simple, 5, ComputeUtils.TimeoutSettings.SLOW);
        cache.remove(key, ComputeUtils.TimeoutSettings.SLOW);
        final Pair<RedisCache.Present, Optional<AppDefinition>> returned = cache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
        final Optional<AppDefinition> returnedFromGet = cache.get(key, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.NOT_PRESENT, returned.getLeft());
        Assert.assertEquals(Optional.empty(), returned.getRight());
        Assert.assertEquals(Optional.empty(), returnedFromGet);
    }

    @Test
    public void setEmptyAndGet() {
        final String key = ServiceWithVersion.makeKey("simple", "1.0.0");
        cache.setEmpty(key, 5, ComputeUtils.TimeoutSettings.SLOW);
        final Pair<RedisCache.Present, Optional<AppDefinition>> returned = cache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
        final Optional<AppDefinition> returnedFromGet = cache.get(key, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.PRESENT, returned.getLeft());
        Assert.assertEquals(Optional.empty(), returned.getRight());
        Assert.assertEquals(Optional.empty(), returnedFromGet);
    }

    @Test
    public void setNullAppDefinition() {
        final String key = ServiceWithVersion.makeKey("simple", "1.0.0");
        cache.set(key, null, 5, ComputeUtils.TimeoutSettings.FAST);
        final Pair<RedisCache.Present, Optional<AppDefinition>> returned = cache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.NOT_PRESENT, returned.getLeft());
        Assert.assertEquals(Optional.empty(), returned.getRight());
    }

    @Test
    public void testNullKey() {
        final AppDefinition simple = AppDefinition.builder().build();
        cache.set(null, simple, 5, ComputeUtils.TimeoutSettings.FAST);
        final Pair<RedisCache.Present, Optional<AppDefinition>> returned = cache.getIfPresent(null, ComputeUtils.TimeoutSettings.FAST);
        final Optional<AppDefinition> returnedFromGet = cache.get(null, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.NOT_PRESENT, returned.getLeft());
        Assert.assertEquals(Optional.empty(), returned.getRight());
        Assert.assertEquals(Optional.empty(), returnedFromGet);

        cache.remove(null, ComputeUtils.TimeoutSettings.SLOW); // Fails, but does not throw

        final String returnedFromGetString = cache.getString(null, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertNull(returnedFromGetString);

        cache.setString(null, "", 10, ComputeUtils.TimeoutSettings.SLOW);  // Fails, but does not throw
    }

    @Test
    public void testClear() {
        // test sad path, no connection
        doReturn(null).when(client).connect();
        cache.clear(ComputeUtils.TimeoutSettings.SLOW);
        verify(metrics, times(0)).reportRedisCacheCleared();
        // test happy path
        doReturn(connection).when(client).connect();
        cache.clear(ComputeUtils.TimeoutSettings.SLOW);
        verify(metrics, times(1)).reportRedisCacheCleared();
    }

    @Test
    public void unconnectedBehavior() {
        doThrow(new RedisConnectionException("mocked")).when(client).connect();
        final String key = ServiceWithVersion.makeKey("simple", "1.0.0");

        cache.setEmpty(key, 5, ComputeUtils.TimeoutSettings.FAST);
        Pair<RedisCache.Present, Optional<AppDefinition>> returned = cache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.NOT_PRESENT, returned.getLeft());

        final AppDefinition simple = AppDefinition.builder().build();
        cache.set(key, simple, 5, ComputeUtils.TimeoutSettings.FAST);
        returned = cache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.NOT_PRESENT, returned.getLeft());

        cache.remove(key, ComputeUtils.TimeoutSettings.FAST);  // Does not throw, but does nothing
        returned = cache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.NOT_PRESENT, returned.getLeft());

        cache.setString(key, "", 5, ComputeUtils.TimeoutSettings.FAST);
        returned = cache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.NOT_PRESENT, returned.getLeft());

        final String returnedString = cache.getString("nothing", ComputeUtils.TimeoutSettings.FAST);
        Assert.assertNull(returnedString);

        final Optional<AppDefinition> returnedFromGet = cache.get("nothing", ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(Optional.empty(), returnedFromGet);
    }

    @Test
    public void testJsonParseError() {
        final AppDefinition simple = AppDefinition.builder().build();
        final String key = ServiceWithVersion.makeKey("simple", "1.0.0");
        doAnswer(invocation -> {
            throw new MyJsonException();
        }).when(cache).setString(anyString(), anyString(), anyInt(), any(ComputeUtils.TimeoutSettings.class));
        cache.set(key, simple, 5, ComputeUtils.TimeoutSettings.SLOW);
        final String notJson = "}}}\\>>>This is not json<<<//{{{";
        doReturn(notJson).when(cache).getString(anyString(), any(ComputeUtils.TimeoutSettings.class));
        final Pair<RedisCache.Present, Optional<AppDefinition>> returned = cache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.PRESENT, returned.getLeft());
        Assert.assertTrue(returned.getRight().isEmpty());
    }

    @Test
    public void testRedisCommandTimeoutError() {
        final AppDefinition simple = AppDefinition.builder().build();
        final String key = ServiceWithVersion.makeKey("simple", "1.0.0");
        doThrow(new RedisCommandTimeoutException("from test")).when(cache).setString(anyString(), anyString(), anyInt(), any(ComputeUtils.TimeoutSettings.class));
        doThrow(new RedisCommandTimeoutException("from test")).when(cache).getString(anyString(), any(ComputeUtils.TimeoutSettings.class));
        cache.set(key, simple, 5, ComputeUtils.TimeoutSettings.SLOW);
        final Pair<RedisCache.Present, Optional<AppDefinition>> returned = cache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.PRESENT, returned.getLeft());
        Assert.assertTrue(returned.getRight().isEmpty());
    }
}
