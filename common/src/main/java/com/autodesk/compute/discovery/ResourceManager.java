package com.autodesk.compute.discovery;

import com.autodesk.compute.common.ComputeAWSStepFunctionsDriver;
import com.autodesk.compute.common.ServiceWithVersion;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.Services;
import com.autodesk.compute.model.cosv2.ApiOperationType;
import com.autodesk.compute.model.cosv2.AppDefinition;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * ResourceManager schedules adf discovery at given period and creates resources when new adf
 * gets discovered and removes them when adf is deleted.
 */
@Slf4j
public class ResourceManager {
    private ComputeAWSStepFunctionsDriver computeAWSStepFunctionsDriver;
    private final SimpleDateFormat dateFormatter = new SimpleDateFormat("dd MM yyyy HH:mm:ss");
    private StateMachineManager stateMachineManager;

    @Getter
    private boolean initialized;

    private static class LazyResourceManagerHolder {    //NOPMD
        public static final ResourceManager INSTANCE = makeResourceManager();

        @SneakyThrows(ComputeException.class)
        private static ResourceManager makeResourceManager() {
            final ResourceManager singleton = new ResourceManager();
            singleton.initialize();
            return singleton;
        }
    }

    public static ResourceManager getInstance() {
        return LazyResourceManagerHolder.INSTANCE;
    }

    /**
     * Creates ResourceManager that schedules adf discovery at given period and creates resources when new adf
     * gets discovered and removes them when adf is deleted
     */
    ResourceManager() {
        initialized = false;
    }

    // This is here so it can be mocked
    public StateMachineManager makeStateMachineManager() {
        return new StateMachineManager();
    }

    // This is here so it can be mocked
    public ComputeAWSStepFunctionsDriver makeComputeAWSStepFunctionsDriver() {
        return new ComputeAWSStepFunctionsDriver();
    }

    public void initialize() throws ComputeException {
        if (initialized) {
            log.warn("ResourceManager constructor called when singleton is non-null");
            return;
        }
        stateMachineManager = makeStateMachineManager();
        computeAWSStepFunctionsDriver = makeComputeAWSStepFunctionsDriver();

        initialized = true;
    }

    public ServiceDiscoveryConfig getServiceDiscoveryConfig() {
        return ServiceDiscoveryConfig.getInstance();
    }

    void forceSyncResources(final Services adfs) {  //NOPMD
        // Collect the top-most app definition for each
        final List<AppDefinition> discoveredServices = adfs.getAppDefinitions().asMap().entrySet()
                .stream().filter(entry -> ServiceWithVersion.isCurrentVersion(
                        ServiceWithVersion.fromKey(entry.getKey()).getPortfolioVersion()))
                .map(entry -> entry.getValue().iterator().next()).collect(Collectors.toUnmodifiableList());

        final CacheActivities activitiesCache = new CacheActivities(computeAWSStepFunctionsDriver);
        final CacheStateMachines stateMachinesCache = new CacheStateMachines(computeAWSStepFunctionsDriver);

        final Stream<AppDefinition> mapEntries = discoveredServices.stream();
        final Stream<AppDefinition> filteredMapEntries = mapEntries.filter(
                item -> {
                    final ServiceResources sr = new ServiceResources(item, ServiceResources.ServiceDeployment.DEPLOYED);
                    final List<String> activityArns = sr.getWorkers().values().stream()
                            .map(ServiceResources.WorkerResources::getActivityArn).collect(toList());
                    final List<String> stateMachineArns = sr.getWorkers().values().stream()
                            .map(ServiceResources.WorkerResources::getStateMachineArn).collect(toList());
                    return !activitiesCache.areActivitiesCreated(activityArns) || !stateMachinesCache.areStateMachinesCreated(stateMachineArns);
                }
        );
        final List<AppDefinition> resourcesThatDoNotExist = filteredMapEntries.collect(toList());
        resourcesThatDoNotExist.forEach(oneAdf -> syncResource(
                oneAdf, Optional.empty(), ApiOperationType.CREATE_OR_UPDATE_APPLICATION));

        if (log.isInfoEnabled())
            log.info("ResourceManager: Successfully synced resources from gatekeeper at {}",
                    dateFormatter.format(Date.from(Instant.now())));
    }

    public void createTestResources(final AppDefinition appDef) {
        final ServiceResources resources = new ServiceResources(appDef, ServiceResources.ServiceDeployment.TEST);
        createOrUpdateResources(Optional.empty(), resources);
    }

    /**
     * Discovers changes to services and creates/removes resources for new/deleted apps
     */
    public ServiceResources syncResource(@NonNull final AppDefinition adf,
                                         final Optional<ServiceResources> existingResources,
                                         final ApiOperationType operationType) {
        final String serviceName = adf.getAppName();

        if (operationType == ApiOperationType.CREATE_OR_UPDATE_APPLICATION) {
            if (existingResources.isPresent())
                log.info("syncResource: Updating resources for service {}", serviceName);
            else
                log.info("syncResource: Creating new resources for service {}", serviceName);

            return createOrUpdateResources(existingResources, new ServiceResources(adf, ServiceResources.ServiceDeployment.DEPLOYED));
        } else if (operationType == ApiOperationType.DELETE_APPLICATION) {
            log.info("Service {} seems to be deleted. Removing related resources.", serviceName);
            deleteResources(existingResources);
        }
        return null;
    }

    /**
     * Creates new resources for an adf, or updates existing ones
     *
     * @param existingResources ServiceResources that are already found in the cache
     * @param newResources      ServiceResources that are in the updated ADF
     * @return ServiceResources with updated resource information
     */
    private ServiceResources createOrUpdateResources(final Optional<ServiceResources> existingResources, final ServiceResources newResources) {
        final Map<String, ServiceResources.WorkerResources> existingMap = existingResources
                .map(ServiceResources::getWorkers)
                .orElse(Collections.emptyMap());
        final Set<String> workerIdentifiers = existingMap.values()
                .stream().map(ServiceResources.WorkerResources::getWorkerIdentifier).collect(Collectors.toSet());

        for (final Map.Entry<String, ServiceResources.WorkerResources> worker : newResources.getWorkers().entrySet()) {
            final ServiceResources.WorkerResources newWorker = worker.getValue();
            boolean createResources = true;
            if (workerIdentifiers.contains(worker.getValue().getWorkerIdentifier())) {
                createResources = false;
                final ServiceResources.WorkerResources existingWorker = existingMap.get(worker.getKey());
                newWorker.setActivityArn(existingWorker.getActivityArn());
                newWorker.setStateMachineArn(existingWorker.getStateMachineArn());
                newWorker.setStateMachine(existingWorker.getStateMachine());
                newWorker.setPortfolioVersion(newResources.getAdf().getPortfolioVersion());

                // We've had cases where the state machine wasn't there and the activity was. So create or update the state machine as well.
                stateMachineManager.createStateMachine(newWorker, existingWorker.getActivityArn(),
                        getServiceDiscoveryConfig().getStateMachineRoleArn());
            }
            if (createResources) {
                newWorker.setPortfolioVersion(newResources.getAdf().getPortfolioVersion());
                log.info("Creating activity {}", newWorker.getActivityName());
                final String activityArn = stateMachineManager.createActivity(newWorker.getActivityName());
                newWorker.setActivityArn(activityArn);
                final String stateMachineArn = stateMachineManager.createStateMachine(
                        newWorker, activityArn, getServiceDiscoveryConfig().getStateMachineRoleArn());
                log.info("Creating state machine {}", stateMachineArn);
                newWorker.setStateMachineArn(stateMachineArn);
            }
        }
        return newResources;
    }

    /**
     * Deletes resources for a service. Public so it can be tested directly.
     *
     * @param serviceResources adf for which to delete resources
     */
    public void deleteResources(final Optional<ServiceResources> serviceResources) {
        if (serviceResources.isEmpty())
            return;
        for (final ServiceResources.WorkerResources worker : serviceResources.get().getWorkers().values()) {
            final String smArn = worker.getStateMachineArn();
            stateMachineManager.deleteStateMachine(smArn);
            final String activityArn = worker.getActivityArn();
            stateMachineManager.deleteActivity(activityArn);
        }
    }

}