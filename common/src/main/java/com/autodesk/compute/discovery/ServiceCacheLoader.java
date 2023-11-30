package com.autodesk.compute.discovery;

import com.autodesk.compute.common.ComputeUtils;
import com.autodesk.compute.common.ServiceWithVersion;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.Services;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Multimap;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import jakarta.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class ServiceCacheLoader extends CacheLoader<String, Optional<ServiceResources>> {
    private final ServiceDiscovery serviceDiscovery;

    @Setter
    private LoadingCache<String, Optional<ServiceResources>> serviceCache;

    @Inject
    public ServiceCacheLoader() {
        this(new ServiceDiscovery());
    }

    public ServiceCacheLoader(final ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    @Override
    public Optional<ServiceResources> load(@NonNull final String serviceKey) throws ComputeException {
        log.info("Could not find {} in service cache, will try to load from Redis and gatekeeper", serviceKey);
        try (final RedisCache<AppDefinition> appDefinitionRedisCache = new RedisCache<>(AppDefinition.class)) {
            final Pair<RedisCache.Present, Optional<AppDefinition>> appDef = appDefinitionRedisCache.getIfPresent(serviceKey, ComputeUtils.TimeoutSettings.FAST);
            final Optional<ServiceResources> resources = appDef.getRight().map(item -> new ServiceResources(item, ServiceResources.ServiceDeployment.DEPLOYED));
            // If we found something in the redis cache, either an ADF or an empty string, then return.
            if (appDef.getLeft() == RedisCache.Present.PRESENT)
                return resources;
        }
        return loadAndCheckDeployedAdf(serviceKey);
    }

    private void loadRedisCache(final Services adfs) {
        try (final RedisCacheLoader loader = makeRedisCacheLoader()) {
            loader.clearAppStateCache();
            final Multimap<String, AppDefinition> adfsMap = adfs.getAppDefinitions();
            // The multimap contains both non-version-specific keys and version-specific ones.
            // The non-version-specific keys keep a stack rank of deployed and test versions.
            // Get the list and put the regular and test versions into the cache.
            final List<String> deployedKeysOnly = adfsMap.keySet().stream()
                    .filter(oneKey -> ServiceWithVersion.isCurrentVersion(ServiceWithVersion.fromKey(oneKey).getPortfolioVersion()))
                    .collect(Collectors.toList());
            for (final String key : deployedKeysOnly) {
                final Collection<AppDefinition> oneMonikerAdfs = adfsMap.get(key);
                boolean first = true;
                for (final AppDefinition oneAppDef : oneMonikerAdfs) {
                    if (first) {
                        loader.addDeployedApplication(oneAppDef);
                        first = false;
                    } else {
                        loader.addTestApplication(oneAppDef);
                    }
                }
            }
        }
    }

    // Public for mocking in tests
    public RedisCacheLoader makeRedisCacheLoader() {
        return RedisCacheLoader.makeInstance();
    }

    private Optional<ServiceResources> addAdfNotFound(final String serviceName, final String portfolioVersion) {
        try (final RedisCacheLoader loader = makeRedisCacheLoader()) {
            loader.addAdfNotFound(serviceName, portfolioVersion);
        }
        return Optional.empty();
    }

    private Optional<ServiceResources> storeDeployedAdfAndReturnIfMatches(final ServiceWithVersion service, final Optional<AppDefinition> optionalApp) {
        // Check to see if the app returned has any workers at all. If it does not, then return Optional.empty() here,
        // so we don't pollute our cache with empty workers.
        final boolean hasComputeSpec = optionalApp.map(AppDefinition::hasComputeSpec).orElse(false);
        if (!hasComputeSpec) {
            return addAdfNotFound(service.getServiceName(), service.getPortfolioVersion());
        }

        return optionalApp.flatMap(app -> {
            final ServiceResources resources = new ServiceResources(app, ServiceResources.ServiceDeployment.DEPLOYED);
            setWorkerStateMachines(resources);
            try (final RedisCacheLoader loader = makeRedisCacheLoader()) {
                loader.addDeployedApplication(app);
            }
            if (ServiceWithVersion.isCurrentVersion(service.getPortfolioVersion()) || service.getPortfolioVersion().equals(app.getPortfolioVersion()))
                return Optional.of(resources);
            return addAdfNotFound(service.getServiceName(), service.getPortfolioVersion());
        });
    }

    private Optional<ServiceResources> loadAndCheckDeployedAdf(final String serviceKey) throws ComputeException {
        // Get the deployed ADF for the specified service key - but it might or might not match the
        // key's portfolio version... so worry about that and handle that too.
        final ServiceWithVersion service = ServiceWithVersion.fromKey(serviceKey);

        final Optional<AppDefinition> optionalApp = serviceDiscovery.getAppFromServiceDiscoveryOrder(service.getServiceName(), "deployed");

        return storeDeployedAdfAndReturnIfMatches(service, optionalApp);
    }

    public Map<String, Optional<ServiceResources>> storeServices(final Services adfs) {
        loadRedisCache(adfs);
        final Map<String, Optional<ServiceResources>> resources = //NOPMD - not concurrent
                adfs.getAppDefinitions().asMap().entrySet().stream().map(oneEntry ->
                        new ImmutablePair<>(
                                oneEntry.getKey(),
                                Optional.of(new ServiceResources(oneEntry.getValue().iterator().next(), ServiceResources.ServiceDeployment.DEPLOYED)))
                ).collect(Collectors.toMap(ImmutablePair::getLeft, ImmutablePair::getRight));
        for (final Optional<ServiceResources> optServiceResources : resources.values()) {
            optServiceResources.ifPresent(this::setWorkerStateMachines);
        }
        if (serviceCache != null)
            serviceCache.putAll(resources);
        return resources;
    }

    @Override
    public Map<String, Optional<ServiceResources>> loadAll(final Iterable<? extends String> serviceKeys) throws ComputeException {
        final Services adfs = serviceDiscovery.getDeployedComputeServices();
        return storeServices(adfs);
    }

    // Public so it can be mocked
    public StateMachineManager getStateMachineManager() {
        return new StateMachineManager();
    }

    private void setWorkerStateMachines(final ServiceResources serviceResources) {
        for (final ServiceResources.WorkerResources workerResources : serviceResources.getWorkers().values()) {
            workerResources.setStateMachine(getStateMachineManager().getStateMachine(workerResources, workerResources.getActivityArn()));
        }
    }
}
