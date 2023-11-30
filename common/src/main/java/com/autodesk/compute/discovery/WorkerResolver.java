package com.autodesk.compute.discovery;

import com.autodesk.compute.common.ComputeUtils;
import com.autodesk.compute.common.ServiceWithVersion;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.Services;
import com.autodesk.compute.model.cosv2.ApiOperationType;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.google.common.base.MoreObjects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.pivovarit.function.ThrowingSupplier;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.common.ServiceWithVersion.*;
import static java.util.stream.Collectors.toList;

@Slf4j
public class WorkerResolver {
    // The redis cache refreshes every 40 days. Just to be safe, we'll allow this
    // once every six hours.
    private static final int MIN_FORCE_SYNC_INTERVAL_SECONDS = 3_600 * 6;

    private LoadingCache<String, Optional<ServiceResources>> serviceResourceCache;
    private LoadingCache<String, Optional<ServiceResources>> testResourceCache;
    private final ServiceDiscovery serviceDiscovery;
    private Instant lastForceSyncTime;
    private ServiceCacheLoader serviceCacheLoader;

    private @Getter
    boolean initialized;

    private static class LazyWorkerResolverHolder {    //NOPMD
        public static final WorkerResolver INSTANCE = makeWorkerResolver();

        @SneakyThrows(ComputeException.class)
        private static WorkerResolver makeWorkerResolver() {
            final WorkerResolver singleton = new WorkerResolver();
            singleton.initialize();
            return singleton;
        }
    }

    public static WorkerResolver getInstance() {
        return WorkerResolver.LazyWorkerResolverHolder.INSTANCE;
    }

    public enum LookupType {
        TEST_ONLY(true, false),
        DEPLOYED_ONLY(false, true),
        DEPLOYED_OR_TEST(true, true);

        private final boolean includesTestVersions;
        private final boolean includesDeployedVersion;  //NOPMD

        LookupType(final boolean includesTestVersions, final boolean includesDeployedVersion) {
            this.includesDeployedVersion = includesDeployedVersion;
            this.includesTestVersions = includesTestVersions;
        }
    }

    /**
     * Creates ResourceManager that schedules adf discovery at given period and creates resources when new adf
     * gets discovered and removes them when adf is deleted
     */
    WorkerResolver() {
        this(new ServiceDiscovery());
    }

    WorkerResolver(final ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
        this.lastForceSyncTime = Instant.EPOCH;
        initialized = false;
    }

    // Mockable
    public ComputeConfig getComputeConfig() {
        return ComputeConfig.getInstance();
    }

    public void initialize() throws ComputeException {
        if (this.isInitialized()) {
            log.warn("ResourceManager constructor called when singleton is non-null");
            return;
        }
        serviceCacheLoader = makeServiceCacheLoader(serviceDiscovery);
        serviceResourceCache = initServiceResourceCache(serviceCacheLoader);
        serviceCacheLoader.setServiceCache(serviceResourceCache);
        // Test caches should be shorter-lived
        testResourceCache = initTestResourceCache();

        // One-time cache build. Putting it here so that the constructor isn't expensive in the tests.
        this.lastForceSyncTime = getCacheForceSyncTime();
        if (noRecentForceSync()) {
            forceSyncResources();
        }
        initialized = true;
    }

    private boolean noRecentForceSync() {
        return Duration.between(lastForceSyncTime, Instant.now()).getSeconds() > MIN_FORCE_SYNC_INTERVAL_SECONDS;
    }

    // For mocking
    public ServiceCacheLoader makeServiceCacheLoader(final ServiceDiscovery localServiceDiscovery) {
        return new ServiceCacheLoader(localServiceDiscovery);
    }

    private void setForceSyncTime(final Instant time) {
        this.lastForceSyncTime = time;
        try (final RedisCacheLoader cache = makeRedisCacheLoader()) {
            cache.setLastForceSyncTime(time);
        }
    }

    /* package */ void forceSyncResources() throws ComputeException {   //NOPMD
        final Services adfs = serviceDiscovery.getDeployedComputeServices();
        getResourceManager().forceSyncResources(adfs);
        // On startup, load the cache once.
        serviceCacheLoader.storeServices(adfs);
        setForceSyncTime(Instant.now());
    }

    public Instant getCacheForceSyncTime() {
        try (final RedisCacheLoader cache = makeRedisCacheLoader()) {
            return cache.getLastForceSyncTime();
        }
    }

    // Public so it can be mocked in tests
    public RedisCacheLoader makeRedisCacheLoader() {
        return RedisCacheLoader.makeInstance();
    }

    public void invalidateResource(@NonNull final String serviceName) {
        log.info("Invalidating service resource cache for service {}", serviceName);
        serviceResourceCache.invalidate(serviceName);
        try (final RedisCacheLoader cache = makeRedisCacheLoader()) {
            cache.invalidate(serviceName);
        }
    }

    private CacheLoader<String, Optional<ServiceResources>> makeTestCacheLoader() {
        return new CacheLoader<>() {
            @Override
            public Optional<ServiceResources> load(final String key) throws ComputeException {
                WorkerResolver.log.info("Could not find {} in test cache, will try to load from Redis, then gatekeeper", key);
                final ServiceWithVersion keyAndVersion = ServiceWithVersion.fromKey(key); //NOPMD
                try (final RedisCache<AppDefinition> appDefinitionRedisCache = makeRedisCache(AppDefinition.class)) {
                    final Pair<RedisCache.Present, Optional<AppDefinition>> appDef = appDefinitionRedisCache.getIfPresent(key, ComputeUtils.TimeoutSettings.FAST);
                    final Optional<ServiceResources> resources = appDef.getRight().map(item -> new ServiceResources(item, ServiceResources.ServiceDeployment.TEST));
                    if (appDef.getLeft() == RedisCache.Present.PRESENT)
                        return resources;
                }
                WorkerResolver.log.info("Test cache moniker {} portfolio version {} not found in Redis; going to Gatekeeper",
                        keyAndVersion.getServiceName(), keyAndVersion.getPortfolioVersion());
                final Optional<AppDefinition> adf = serviceDiscovery.getAppFromServiceDiscoveryOrder(keyAndVersion.getServiceName(), keyAndVersion.getPortfolioVersion());

                try (final RedisCacheLoader loader = makeRedisCacheLoader()) {
                    if (adf.isPresent()) {
                        final AppDefinition appDef = adf.get();
                        getResourceManager().createTestResources(appDef);
                        loader.addTestApplication(adf.get());
                    } else {
                        loader.addAdfNotFound(keyAndVersion.getServiceName(), keyAndVersion.getPortfolioVersion());
                    }
                }

                return adf.map(appDef -> new ServiceResources(appDef, ServiceResources.ServiceDeployment.TEST));
            }
        };
    }

    // For mocking
    public ResourceManager getResourceManager() {
        return ResourceManager.getInstance();
    }

    // For mocking
    public <T> RedisCache<T> makeRedisCache(final Class clazz) {
        return new RedisCache<T>(clazz);
    }

    /**
     * Gets WorkerResources class that holds resources information created for an adf
     * Looks up based on service:worker and refreshes the cache if needed
     *
     * @param serviceName name of the service
     * @param workerName  name of the worker
     * @return WorkerResources class that has information regarding the resources for the adf
     * @throws ComputeException
     */
    public Optional<ServiceResources.WorkerResources> getWorkerResources(
            @NonNull final String serviceName, @NonNull final String workerName, final String portfolioVersion)
            throws ComputeException {
        return getWorkerResourcesWithOptionalForceSync(
                serviceName, workerName, portfolioVersion, true
        );
    }

    public Optional<ServiceResources> getLocalServiceResources(
            @NonNull final String serviceName
    ) {
        final Optional<ServiceResources> existing = serviceResourceCache.getIfPresent(serviceName);
        return (existing == null ? Optional.empty() : existing);
    }

    public void addServiceResources(@NonNull final AppDefinition service, final ApiOperationType operationType) {
        final Optional<ServiceResources> existingResources = getLocalServiceResources(service.getAppName());
        final ServiceResources resources = getResourceManager().syncResource(service, existingResources, operationType);
        getResourceManager().createTestResources(service);
        if (resources != null)
            serviceResourceCache.put(service.getAppName(), Optional.of(resources));
        else
            serviceResourceCache.invalidate(service.getAppName());
    }

    public Optional<ServiceResources.WorkerResources> getWorkerResourcesWithOptionalForceSync(
            @NonNull final String serviceName,
            @NonNull final String workerName,
            final String portfolioVersion,
            final boolean forceSyncIfMissing)
            throws ComputeException {
        Optional<ServiceResources.WorkerResources> workerResources;

        workerResources = lookupWorker(serviceName, workerName, portfolioVersion);

        // If we find no worker it might be possible resource manager missed SNS adf update notification
        // so try to force sync once
        if (workerResources.isEmpty() && forceSyncIfMissing && noRecentForceSync()) {
            log.info("Worker {}:{} is not present in cache, forceSyncResources from gatekeeper", serviceName, workerName);
            forceSyncResources();
            workerResources = lookupWorker(serviceName, workerName, portfolioVersion);
        }

        return workerResources;
    }

    private LoadingCache<String, Optional<ServiceResources>> initTestResourceCache() {
        return CacheBuilder.newBuilder().expireAfterWrite(120, TimeUnit.MINUTES).build(makeTestCacheLoader());
    }

    private LoadingCache<String, Optional<ServiceResources>> initServiceResourceCache(final ServiceCacheLoader loader) {
        return CacheBuilder.newBuilder().refreshAfterWrite(60, TimeUnit.SECONDS)
                .removalListener(notification ->
                        log.debug("ServiceResourceCache: {} was evicted", notification.getKey())
                )
                .build(loader);
    }

    private Optional<ServiceResources> serviceResourcesFromRedis(final String serviceKey, final WorkerResolver.LookupType lookupType) {
        try (final RedisCache<AppDefinition> redisCache = makeRedisCache(AppDefinition.class)) {
            final Pair<RedisCache.Present, Optional<AppDefinition>> appDef = redisCache.getIfPresent(serviceKey, ComputeUtils.TimeoutSettings.FAST);
            if (appDef.getLeft() == RedisCache.Present.NOT_PRESENT || appDef.getRight().isEmpty())
                return Optional.empty();
            ServiceResources.ServiceDeployment computeDeploymentType = ServiceResources.ServiceDeployment.DEPLOYED;
            if (lookupType.includesTestVersions && !isCurrentVersion(fromKey(serviceKey).getPortfolioVersion()))
                computeDeploymentType = ServiceResources.ServiceDeployment.TEST;
            // This dance is here because lambdas require final variables.
            final ServiceResources.ServiceDeployment deploymentType = computeDeploymentType;
            return appDef.getRight().map(item -> new ServiceResources(item, deploymentType));
        }
    }

    private Optional<ServiceResources> firstNonEmpty(final List<ThrowingSupplier<Optional<ServiceResources>, ExecutionException>> suppliers) throws ExecutionException {
        for (final ThrowingSupplier<Optional<ServiceResources>, ExecutionException> oneSupplier : suppliers) {
            final Optional<ServiceResources> result = oneSupplier.get();
            if (result.isPresent())
                return result;
        }
        return Optional.empty();
    }

    /**
     * Looks for a given service in the cached set of discovered compute services
     * Can also use makeKey(service, portfolioVersion) as serviceKey to search for
     * a versioned service resource
     *
     * @param serviceKey service moniker, or versioned service key e.g. makeKey(service, portfolioVersion)
     * @param lookupType enum to indicate whether the testResourceCache should be examined
     * @return Return Optional<ServiceResources> the given service resources, if found
     */
    private Optional<ServiceResources> lookupService(final String serviceKey, final WorkerResolver.LookupType lookupType, final boolean cacheOnly)
            throws ComputeException {
        try {
            final List<ThrowingSupplier<Optional<ServiceResources>, ExecutionException>> suppliers = Lists.newLinkedList();
            // InMemory
            // If it's a test request; look in that cache first
            if (lookupType.includesTestVersions) {
                suppliers.add(() -> MoreObjects.firstNonNull(this.testResourceCache.getIfPresent(serviceKey), Optional.empty()));
            }
            // If it's a test request; look at the stable cache second. If it's not a test request; only look at the stable cache
            suppliers.add(() -> MoreObjects.firstNonNull(this.serviceResourceCache.getIfPresent(serviceKey), Optional.empty()));
            // Redis
            suppliers.add(() -> this.serviceResourcesFromRedis(serviceKey, lookupType));

            // Gatekeeper
            // If cacheOnly, we only getIfPresent in the cache.
            // If !cacheOnly, we force the cache to load results from the source of truth
            if (!cacheOnly) {
                if (lookupType.includesTestVersions) {
                    suppliers.add(() -> this.testResourceCache.get(serviceKey));
                }

                suppliers.add(() -> this.serviceResourceCache.get(serviceKey));
            }

            // Depending on configuration, this should look first either in the service cache, or the test resource cache.
            // Then it will look on Redis
            // Then optionally force a re-sync of the test cache and/or service cache
            return firstNonEmpty(suppliers);
        } catch (final ExecutionException e) {
            log.error(makeString("getServiceResources: Error while looking for ", serviceKey, " resources. Error: "), e);
            if (e.getCause() instanceof ComputeException) {
                throw (ComputeException) e.getCause();
            }
            return Optional.empty();
        }
    }

    /**
     * Look up our service and worker within
     *
     * @param serviceName      The service whose worker we want
     * @param workerName       The worker within the service
     * @param portfolioVersion The portfolio version of the worker and service
     * @return The worker if one exists
     * @throws ComputeException
     */
    /*package*/ Optional<ServiceResources.WorkerResources> lookupWorker(    //NOPMD
                                                                            @NonNull final String serviceName, @NonNull final String workerName, final String portfolioVersion)
            throws ComputeException {
        try {
            final Optional<ServiceResources> serviceResources;
            // If it's a test request, then it must specify a portfolio version. If it does,
            // check that cache first, and then fall back to the primary one.
            final WorkerResolver.LookupType lookupType = isCurrentVersion(portfolioVersion) ? WorkerResolver.LookupType.DEPLOYED_ONLY : WorkerResolver.LookupType.DEPLOYED_OR_TEST;
            serviceResources = lookupService(makeKey(serviceName, portfolioVersion), lookupType, false);

            if (serviceResources.isPresent()) {
                log.info(makeString("LookupWorker: Found service ", serviceName,
                        " with portfolio version ",
                        portfolioVersion, " in the resource caches with resolved portfolio version ", serviceResources.get().getAdf().getPortfolioVersion()));
            } else {
                log.info(makeString("LookupWorker: No service found for ", serviceName,
                        " with portfolio version ", portfolioVersion));
            }
            return serviceResources.map(resource -> resource.findWorker(workerName));
        } catch (final Exception e) {
            log.error(makeString("getServiceResources: Error while reading adf ", serviceName, " resources. Error: "), e);
            if (e.getCause() instanceof ComputeException) {
                throw (ComputeException) e.getCause();
            }
            return Optional.empty();
        }
    }

    // Callable during tests
    void refreshIfEmpty() { //NOPMD
        // Very carefully reload things in the caches for the current portfolio version.
        // During the initial deployment, this key should not actually be found, either in Redis
        // or the Gatekeeper's get all deployed apps API call.
        if (serviceResourceCache.size() == 0) {
            final String serviceKey = makeKey(getComputeConfig().getAppMoniker(), getComputeConfig().getPortfolioVersion());
            serviceResourceCache.refresh(serviceKey);
            // This one should actually populate the test cache, since the version is available.
            // It should also create the resources for the test job.
            testResourceCache.refresh(serviceKey);
        }
    }

    /**
     * Looks for a given service in the cached set of discovered compute services
     * Can also use makeKey(service, portfolioVersion) as serviceKey to search for
     * a versioned service resource.
     * <p>
     * Note, this will NOT induce a gatekeeper lookup, thus there is a chance a newly
     * registered resource may not be found.  Use this method with that risk in mind.
     * Submitting a job and polling for a job are the first things our customers do, and
     * if bad configuration is something that can be specifically identified, we should
     * do that.The initial intent for writing this method is to facilitate providing
     * specific error messaging in failed calls to getWorkerResources in the Job and Poll
     * api implementations.
     *
     * @param serviceKey service moniker, or versioned service key e.g. makeKey(service, portfolioVersion)
     * @return Return true if the given service was found
     */
    public boolean hasService(final String serviceKey) {
        try {
            final Optional<ServiceResources> serviceResources = lookupService(serviceKey, WorkerResolver.LookupType.DEPLOYED_OR_TEST, true);
            return serviceResources != null && serviceResources.isPresent();
        } catch (final ComputeException e) {
            // nom nom
        }
        return false;
    }

    /**
     * Gets list of discovered compute services
     *
     * @return Return list of discovered compute AppDefinition
     */
    public List<AppDefinition> getServices() {
        return serviceResourceCache.asMap().values().stream().filter(Optional::isPresent).map(item -> item.get().getAdf()).collect(toList());
    }


}
