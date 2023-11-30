package com.autodesk.compute.discovery;

import com.autodesk.compute.common.ComputeUtils;
import com.autodesk.compute.common.ComputeUtils.TimeoutSettings;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.configuration.ComputeConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisKeyCommands;
import io.lettuce.core.api.sync.RedisServerCommands;
import io.lettuce.core.api.sync.RedisStringCommands;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static com.autodesk.compute.common.ComputeStringOps.isNullOrEmpty;
import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Slf4j
public class RedisCache<T> implements AutoCloseable {
    public enum Present {
        PRESENT,
        NOT_PRESENT
    }

    private static final long REDIS_CALL_TIMEOUT_FAST = 2L;
    private static final long REDIS_CONNECTION_TIMEOUT_FAST = 2L;
    private static final long REDIS_CALL_TIMEOUT_SLOW = 5L;
    private static final long REDIS_CONNECTION_TIMEOUT_SLOW = 5L;

    // RedisClient is an expensive, thread-safe resource.
    private RedisClient client;

    private StatefulRedisConnection<String, String> connection;
    private final Class<T> type;
    private final Metrics metrics;
    private final String namespace;
    private final RedisURI redisUri;

    public static String makeNamespace() {
        return makeString(ComputeConfig.getInstance().getAppMoniker(), "-", ComputeConfig.getInstance().getPortfolioVersion(), ":");
    }

    @Inject
    public RedisCache(final Class<T> type) {
        this(type, ComputeConfig.getInstance().getRedisUri(), makeNamespace(), Metrics.getInstance());
    }

    // For mocking
    public RedisClient makeRedisClient(final RedisURI redisURI) {
        if (redisURI == null)
            return null;
        return RedisClient.create(RedisClientResourcesSingleton.getInstance(), redisURI);
    }

    public RedisCache(final Class<T> type, final RedisURI redisUri, final String namespace, final Metrics metrics) {
        this.type = type;
        this.namespace = namespace;
        this.redisUri = redisUri;
        this.metrics = metrics;
    }

    private void ensureRedisClient() {
        if (client != null)
            return;
        client = makeRedisClient(this.redisUri);
    }

    @SneakyThrows(InterruptedException.class)
    public boolean ensureConnected(final ComputeUtils.TimeoutSettings timeout) {
        final Instant callStart = Instant.now();

        ensureRedisClient();
        if (connection == null) {
            if (client == null)
                return false;

            final long connectTimeoutSeconds = (timeout == TimeoutSettings.SLOW)
                    ? REDIS_CONNECTION_TIMEOUT_SLOW
                    : REDIS_CONNECTION_TIMEOUT_FAST;
            boolean retry;
            do {
                try {
                    retry = false;
                    // here we set the connection timeout on the client before (re)connecting
                    final ClientOptions clientOptions = ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build()).build();
                    client.setOptions(clientOptions);
                    // attempting connect to redis
                    this.connection = client.connect();
                } catch (final Exception e) {
                    final Duration durationAttemptConnect = Duration.between(callStart, Instant.now());
                    // we allow half the connection timeout (milliseconds) to fail and retry
                    final long allowableFailureDurationMillis = connectTimeoutSeconds * 500L;
                    retry = durationAttemptConnect.toMillis() < allowableFailureDurationMillis;
                    if (retry) {
                        // rest for 1/4 the time of the allowable failure duration before retry
                        Thread.sleep(allowableFailureDurationMillis / 4L);
                    } else {
                        log.warn("Error connecting to redis ", e);
                    }
                }
            } while (retry);
            if (this.connection == null) {
                return false;
            }
        }
        // here we set the call timeout specifically (call happens in the caller of this method)
        this.connection.setTimeout(Duration.ofSeconds(timeout == TimeoutSettings.SLOW
                ? REDIS_CALL_TIMEOUT_SLOW : REDIS_CALL_TIMEOUT_FAST));
        return true;
    }

    public String namespace(@NonNull final String key) {
        return namespace + key;
    }

    public Pair<Present, Optional<T>> getIfPresent(final String serviceKey, final ComputeUtils.TimeoutSettings timeout) {
        if (!isValidServiceKey(serviceKey)) {
            return Pair.of(Present.NOT_PRESENT, Optional.empty());
        }
        if (!ensureConnected(timeout)) {
            // If no Redis, act as if it's not there.
            metrics.reportRedisCacheMiss();
            return Pair.of(Present.NOT_PRESENT, Optional.empty());
        }
        try {
            final String cacheString = getString(serviceKey, timeout);
            // Null and empty mean subtly different things now.
            // Null means not found, never resolved.
            // Empty means: we tried to find it in the past and failed.
            if (cacheString == null) {
                metrics.reportRedisCacheMiss();
                metrics.reportRedisCacheEntryNotFound();
                return Pair.of(Present.NOT_PRESENT, Optional.empty());
            } else if (cacheString.isEmpty()) {
                metrics.reportRedisCacheHit();
                return Pair.of(Present.PRESENT, Optional.empty());
            }

            final T result = Json.mapper.readValue(cacheString, type);
            metrics.reportRedisCacheHit();
            return Pair.of(Present.PRESENT, Optional.of(result));
        } catch (final JsonProcessingException e) {
            metrics.reportRedisCacheError();
            log.error(makeString("Error parsing the ", type.getCanonicalName(), " cache from Redis for key ", namespace(serviceKey)), e);
            return Pair.of(Present.PRESENT, Optional.empty());
        } catch (final Exception e) {
            metrics.reportRedisCacheError();
            log.error(makeString("Error reading the ", type.getCanonicalName(), " cache from Redis for key ", namespace(serviceKey)), e);
            return Pair.of(Present.PRESENT, Optional.empty());
        }

    }

    public Optional<T> get(final String serviceKey, final ComputeUtils.TimeoutSettings timeout) {
        return getIfPresent(serviceKey, timeout).getRight();
    }

    private String addType(final String cacheKey) {
        return cacheKey + "-" + type.getName();
    }

    public void setEmpty(final String serviceKey, final int ttlSeconds, final ComputeUtils.TimeoutSettings timeout) {
        setString(serviceKey, "", ttlSeconds, timeout);
    }

    public boolean isValidServiceKey(final String serviceKey) {
        if (isNullOrEmpty(serviceKey)) {
            log.warn("Redis cache: null service key passed in; operation ignored");
            return false;
        }
        return true;
    }

    public void set(final String serviceKey, final T obj, final int ttlSeconds, final ComputeUtils.TimeoutSettings timeout) {
        if (!isValidServiceKey(serviceKey))
            return;
        if (!ensureConnected(timeout)) {
            log.warn(makeString("Redis cache: no connection; write to key ", namespace(serviceKey), " ignored"));
            return;
        }
        if (obj == null) {
            log.warn(makeString("Redis cache: null object; write to key ", namespace(serviceKey), " ignored"));
            return;
        }
        try {
            final String message = Json.mapper.writeValueAsString(obj);
            setString(serviceKey, message, ttlSeconds, timeout);
        } catch (final JsonProcessingException e) {
            log.error(makeString("Exception converting an object of type ",
                    type.getCanonicalName(), " to json before writing to Redis cache: "), e);
        } catch (final Exception e) {
            log.error(makeString("Error writing an object of type ",
                    type.getCanonicalName(), " to Redis cache: "), e);
        }
    }

    public String getString(final String serviceKey, final ComputeUtils.TimeoutSettings timeout) {
        if (!isValidServiceKey(serviceKey)) {
            return null;
        }
        if (!ensureConnected(timeout)) {
            return null;
        }
        final RedisStringCommands<String, String> commands = connection.sync();
        return commands.get(namespace(addType(serviceKey)));
    }

    public void setString(final String serviceKey, final String message, final int ttlSeconds, final ComputeUtils.TimeoutSettings timeout) {
        if (!isValidServiceKey(serviceKey)) {
            return;
        }
        if (!ensureConnected(timeout)) {
            return;
        }
        final RedisStringCommands<String, String> commands = connection.sync();
        commands.set(namespace(addType(serviceKey)), message, SetArgs.Builder.ex(ttlSeconds));
        metrics.reportRedisWrite();
    }

    public void remove(final String serviceKey, final ComputeUtils.TimeoutSettings timeout) {
        if (!ensureConnected(timeout) || !isValidServiceKey(serviceKey)) {
            return;
        }

        final RedisKeyCommands<String, String> commands = connection.sync();
        commands.expire(namespace(addType(serviceKey)), 0);
    }

    public void clear(final ComputeUtils.TimeoutSettings timeout) {
        if (!ensureConnected(timeout)) {
            return;
        }

        final RedisServerCommands<String, String> commands = connection.sync();
        commands.flushdb();
        metrics.reportRedisCacheCleared();
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }
}
