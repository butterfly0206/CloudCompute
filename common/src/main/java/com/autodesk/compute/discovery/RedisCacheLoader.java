package com.autodesk.compute.discovery;

import com.autodesk.compute.common.ComputeUtils;
import com.autodesk.compute.common.ServiceWithVersion;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.cosv2.AppStateDriver;
import com.autodesk.compute.cosv2.AppStateDriverImpl;
import com.autodesk.compute.cosv2.DynamoDBDriverImpl;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.autodesk.compute.model.cosv2.AppState;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Instant;
import java.util.Optional;

import static java.lang.System.exit;

@Slf4j
@AllArgsConstructor
public class RedisCacheLoader implements AutoCloseable {
    private final RedisCache<AppDefinition> appDefinitionCache;
    private final RedisCache<AppState> appStateCache;
    private final RedisCache<String> timeCache;
    private static final AppStateDriver dynamoDBDriver;
    private static final ComputeConfig computeConfig;

    public static final int VERSION_SPECIFIC_CACHE_DURATION = 3600 * 24 * 40;    // Version-specific data lives in the cache for 40 days

    static {
        ComputeConfig computeConfigTemp = null;
        try {
            computeConfigTemp = ComputeConfig.getInstance();
        } catch (final Exception e) {
            log.error("Bad configuration. Error Initializing RedisCacheLoader", e);
            exit(1);
        }
        computeConfig = computeConfigTemp;
        dynamoDBDriver = new AppStateDriverImpl(
            new DynamoDBDriverImpl<>(AppState.class, computeConfig.getDbGatekeeperStateTable()));
    }

    public static RedisCacheLoader makeInstance() {
        return new RedisCacheLoader(new RedisCache<>(AppDefinition.class), new RedisCache<>(AppState.class), new RedisCache<>(String.class));
    }

    public void addDeployedApplication(final AppDefinition app) {
        appDefinitionCache.set(ServiceWithVersion.makeKey(app.getAppName()), app, VERSION_SPECIFIC_CACHE_DURATION, ComputeUtils.TimeoutSettings.SLOW);
        addTestApplication(app);
    }

    public void addTestApplication(final AppDefinition app) {
        appDefinitionCache.set(ServiceWithVersion.makeKey(app.getAppName(), app.getPortfolioVersion()), app, VERSION_SPECIFIC_CACHE_DURATION, ComputeUtils.TimeoutSettings.SLOW);
    }

    public void addAdfNotFound(final String moniker, final String portfolioVersion) {
        appDefinitionCache.setEmpty(ServiceWithVersion.makeKey(moniker, portfolioVersion), VERSION_SPECIFIC_CACHE_DURATION, ComputeUtils.TimeoutSettings.SLOW);
    }

    public void addAppState(final AppState appState) {
        appStateCache.set(appState.getAppName(), appState, VERSION_SPECIFIC_CACHE_DURATION, ComputeUtils.TimeoutSettings.SLOW);
    }

    public void clearAppStateCache() {
        appStateCache.clear(ComputeUtils.TimeoutSettings.SLOW);
    }

    // Suppressing "Optional value should only be accessed after calling isPresent()"
    // because the .NOT_PRESENT check guarantees the get will always succeed
    @SuppressWarnings("squid:S3655")
    public AppState getAppState(final String appName) {
        final Pair<RedisCache.Present, Optional<AppState>> fromCache =
                appStateCache.getIfPresent(appName, ComputeUtils.TimeoutSettings.FAST);
        if (fromCache.getLeft() == RedisCache.Present.NOT_PRESENT || fromCache.getRight().isEmpty()) {
            // I'm not totally thrilled this is here, but it's small - maybe move it if this metastasizes. jk 200602
            final AppState appState = dynamoDBDriver.load(appName);
            addAppState(appState);
            return appState;
        }
        return fromCache.getRight().get();
    }

    // Suppressing "Optional value should only be accessed after calling isPresent()"
    // because the .NOT_PRESENT check guarantees the get will always succeed
    @SuppressWarnings("squid:S3655")
    public Instant getLastForceSyncTime() {
        final Pair<RedisCache.Present, Optional<String>> fromCache =
                timeCache.getIfPresent("LastForceSyncTime", ComputeUtils.TimeoutSettings.FAST);
        if (fromCache.getLeft() == RedisCache.Present.NOT_PRESENT || fromCache.getRight().isEmpty()) {
            return Instant.EPOCH;   // First time for this deployment
        }
        return Instant.ofEpochMilli(Long.parseLong(fromCache.getRight().get()));
    }

    public void setLastForceSyncTime(final Instant time) {
        final String timeMillisStr = Long.toString(time.toEpochMilli());
        timeCache.set("LastForceSyncTime", timeMillisStr, VERSION_SPECIFIC_CACHE_DURATION, ComputeUtils.TimeoutSettings.SLOW);
    }

    public void invalidate(final String cacheKey) {
        appDefinitionCache.remove(cacheKey, ComputeUtils.TimeoutSettings.SLOW);
        invalidateAppState(cacheKey);
    }

    public void invalidateAppState(final String cacheKey) {
        appStateCache.remove(cacheKey, ComputeUtils.TimeoutSettings.SLOW);
    }

    @Override
    public void close() {
        if (appDefinitionCache != null) {
            appDefinitionCache.close();
        }
        if (timeCache != null) {
            timeCache.close();
        }
        if (appStateCache != null) {
            appStateCache.close();
        }
    }
}
