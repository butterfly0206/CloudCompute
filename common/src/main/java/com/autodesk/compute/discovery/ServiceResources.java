package com.autodesk.compute.discovery;

import com.amazonaws.services.stepfunctions.builder.StateMachine;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.cosv2.NameAssigner;
import com.autodesk.compute.model.cosv2.*;
import com.autodesk.compute.util.JsonHelper;
import com.google.common.base.MoreObjects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.autodesk.compute.model.cosv2.AppDefinition.TEST_PORTFOLIO_VERSION;
import static java.lang.System.exit;
import static java.util.Optional.ofNullable;

/**
 * ServiceResources generates unique resource names based on AppDefinition provided
 */
@Slf4j
@ToString
public class ServiceResources {
    private static final ComputeConfig computeConfig;

    public enum ServiceDeployment {
        DEPLOYED,
        TEST
    }

    @Getter
    @Setter(AccessLevel.PRIVATE)
    private Map<String, WorkerResources> workers;

    static {
        ComputeConfig computeConfigTemp = null;
        try {
            computeConfigTemp = ComputeConfig.getInstance();
        } catch (final Exception e) {
            log.error("Bad configuration. Error Initializing ServiceResources", e);
            exit(1);
        }
        computeConfig = computeConfigTemp;
    }

    @Getter
    private final AppDefinition adf;

    /**
     *  WorkerResources manages the Step resources of the worker
     */
    public abstract static class WorkerResources {

        private static final ComputeConfig computeConfig;

        static {
            ComputeConfig computeConfigTemp = null;
            try {
                computeConfigTemp = ComputeConfig.getInstance();
            } catch (final Exception e) {
                log.error("Bad configuration. Error Initializing WorkerResources", e);
                exit(1);
            }
            computeConfig = computeConfigTemp;
        }

        @Getter
        protected final String activityName;

        @Getter
        protected final String stateMachineName;

        @Getter
        @Setter
        private String activityArn;

        @Getter
        @Setter
        private String stateMachineArn;

        @Getter
        @Setter
        public StateMachine stateMachine;

        @Getter
        private final String workerIdentifier;

        @Getter
        @Setter
        private String portfolioVersion;

        @Getter
        private final ComputeSpecification computeSpecification;

        @Getter
        private final ServiceResources.ServiceDeployment deploymentType;

        @Getter
        private final List<DeploymentDefinition> deployments;


        protected WorkerResources(final ComputeSpecification computeSpec, final String workerIdentifier, final String portfolioVersion, final ServiceResources.ServiceDeployment deploymentType, final List<DeploymentDefinition> deployments) {
            this.deploymentType = deploymentType;
            computeSpecification = computeSpec;
            this.workerIdentifier = workerIdentifier;
            activityName = String.format("fpcc_%s_activity", workerIdentifier);
            stateMachineName = String.format("fpcc_%s_step", workerIdentifier);
            stateMachineArn = getStateMachineArn(stateMachineName);
            activityArn = getActivityArn(activityName);
            this.portfolioVersion = portfolioVersion;
            this.deployments = deployments;
        }

        /**
         * Gets ARN of the state machine by it's name
         *
         * @param stateMachineName
         * @return
         */
        private static String getStateMachineArn(final String stateMachineName) {

            return String.format("arn:aws:states:%s:%s:stateMachine:%s",
                    computeConfig.getRegion().toLowerCase(), computeConfig.getAwsAccountId(), stateMachineName);
        }

        /**
         * Gets ARN for activity by its name
         *
         * @param activityName the name of activity
         * @return
         */
        private static String getActivityArn(final String activityName) {

            return String.format("arn:aws:states:%s:%s:activity:%s",
                    computeConfig.getRegion().toLowerCase(), computeConfig.getAwsAccountId(), activityName);
        }

    }

    public static class BatchWorkerResources extends WorkerResources {

        private static final String DEFAULT = "default"; // lowercase for v2

        @Getter
        private final BatchJobDefinition jobDefinition;

        @Getter
        private final String batchDefinitionName;

        @Getter
        private final String appName;

        // The workerName value in computeSpecification is not yet required.  When it is, we should remove the
        // workerName constructor arg here and just get it from computeSpec
        // https://jira.autodesk.com/browse/COSV2-1906
        public BatchWorkerResources(final BatchJobDefinition batchJobDef, final String workerIdentifier, final String batchDefinitionName, final String appName, final String portfolioVersion, final ServiceResources.ServiceDeployment deploymentType, final List<DeploymentDefinition> deployments) {
            super(batchJobDef.getComputeSpecification(), workerIdentifier, portfolioVersion, deploymentType, deployments);
            jobDefinition = batchJobDef;
            this.batchDefinitionName = batchDefinitionName;
            this.appName = appName;
        }

        public String getBatchQueue() {
            final List<DeploymentDefinition> deployments = getDeployments();
            final String accountKey = (deployments.isEmpty()
                    || deployments.get(0).getAccountId().compareTo(computeConfig.getAwsAccountId()) == 0) //NOPMD
                    ? DEFAULT : deployments.get(0).getAccountId();

            // cross account batch queue name convention - replace [cos-moniker]-spot with [worker-moniker]-spot
            return MoreObjects.firstNonNull(ofNullable(getJobDefinition())
                            .map(BatchJobDefinition::getContainerProperties)
                            .map(BatchJobDefinition.BatchContainerDefinition::getGpu)
                            .map(gpu -> computeConfig.getBatchGPUQueue().get(accountKey))
                            .orElse(computeConfig.getBatchQueue().get(accountKey)),
                    ofNullable(getJobDefinition())
                            .map(BatchJobDefinition::getContainerProperties)
                            .map(BatchJobDefinition.BatchContainerDefinition::getGpu)
                            .map(gpu -> computeConfig.getBatchGPUQueue().get(DEFAULT))
                            .orElse(computeConfig.getBatchQueue().get(DEFAULT)).replaceAll(computeConfig.getAppMoniker(), this.appName)
            ) + "_0"; // workaround to disable redundancy before terraform removing this feature
        }

        public String getBatchDefinitionName() {
            // If batch worker is cross account, firstly try to pick up the jobDefinition from v3 pipeline notification
            // then fallback to v2 name convention way
            final List<DeploymentDefinition> deployments = getDeployments();
            return (deployments.isEmpty()
                    || deployments.get(0).getAccountId().compareTo(computeConfig.getAwsAccountId()) == 0) //NOPMD
                    ? this.batchDefinitionName : this.jobDefinition.getJobDefinitionName();
        }

    }

    public static class ComponentWorkerResources extends WorkerResources {

        @Getter
        private final ComponentDefinition componentDefinition;

        @Getter
        private final String ecsServiceName;

        public ComponentWorkerResources(final ComponentDefinition componentDef, final String workerIdentifier, final String ecsServiceName, final String portfolioVersion, final ServiceResources.ServiceDeployment deploymentType, final List<DeploymentDefinition> deployments) {
            super(componentDef.getComputeSpecification(), workerIdentifier, portfolioVersion, deploymentType, deployments);
            componentDefinition = componentDef;
            this.ecsServiceName = ecsServiceName;
        }
    }

    // Public for mocking in tests
    public RedisCacheLoader makeRedisCacheLoader() {
        return RedisCacheLoader.makeInstance();
    }

    public ServiceResources(final AppDefinition adf, final ServiceDeployment serviceDeployment) {
        if (adf == null) {
            this.adf = null;
            return;
        }
        /*
            isMultiVersion: is used by NameAssigner to determine if deployed portfolio is test and if so append
            portfolio version (if true) to worker resources like step function, activity etc. In cosv3 we do not have portfolio version
            but adf.getPortfolioVersion() will return 'test' or 'release' based on the deployment type. If it's 'test'; which means
            pre-deploy tests are enabled, and we would like to differentiate resources by adding 'test' in the name.
         */
        boolean isMultiVersion;
        if (ComputeConfig.isCosV3()) {
            isMultiVersion = TEST_PORTFOLIO_VERSION.equalsIgnoreCase(adf.getPortfolioVersion());
        } else {
            try (final RedisCacheLoader loader = makeRedisCacheLoader()) {
                final AppState appState = loader.getAppState(adf.getAppName());
                isMultiVersion = appState.isMultiVersion();
            }
        }
        workers = new HashMap<>();

        final NameAssigner nameAssigner = new NameAssigner(adf.getAppName(), adf.getPortfolioVersion(),
                    isMultiVersion, computeConfig.getCloudosMoniker());

        final List<BatchJobDefinition> batches = MoreObjects.firstNonNull(adf.getBatches(), Collections.emptyList());
        for (final BatchJobDefinition batch : batches) {
            if (batch.getComputeSpecification() != null) {
                final String workerName = MoreObjects.firstNonNull(batch.getComputeSpecification().getWorkerName(), batch.getJobDefinitionName());
                final String batchDefinitionName = nameAssigner.getBatchJobDefinitionName(workerName);

                // For deployed worker, use unversioned worker ID to keep resource names consistent as version changes.
                // This will keep CloudWatch alarm names portfolio version independent
                final String workerIdentifier = serviceDeployment == ServiceDeployment.DEPLOYED ? nameAssigner.getUnversionedWorkerId(workerName) : batchDefinitionName;
                final List<DeploymentDefinition> deployments = JsonHelper.getDeploymentsFromAdf(adf);
                workers.put(workerName, new BatchWorkerResources(batch, workerIdentifier, batchDefinitionName, adf.getAppName(), adf.getPortfolioVersion(), serviceDeployment, deployments));
            }
        }

        final List<ComponentDefinition> components = MoreObjects.firstNonNull(adf.getComponents(), Collections.emptyList());
        for (final ComponentDefinition component : components) {
            if (component.getComputeSpecification() != null) {
                // The workerName value in computeSpecification is not yet required, fail over
                // to the component def name https://jira.autodesk.com/browse/COSV2-1906 makes it
                // required and then this needs no Optional
                final String workerName = ofNullable(component.getComputeSpecification().getWorkerName())
                        .orElse(component.getComponentName());
                final String ecsServiceName = nameAssigner.getEcsServiceName(workerName);

                // For deployed worker, use unversioned worker ID to keep resource names consistent as version changes.
                // This will keep CloudWatch alarm names portfolio version independent
                final String workerIdentifier = serviceDeployment == ServiceDeployment.DEPLOYED ? nameAssigner.getUnversionedWorkerId(workerName) : ecsServiceName;
                final List<DeploymentDefinition> deployments = JsonHelper.getDeploymentsFromAdf(adf);
                workers.put(workerName, new ComponentWorkerResources(component, workerIdentifier, ecsServiceName, adf.getPortfolioVersion(), serviceDeployment, deployments));
            }
        }

        // Only set our adf field if a compute worker was found in one of the possible definitions
        this.adf = workers.isEmpty() ? null : adf;
    }

    /*
     * Just a map look up, but later could implement semver
     */
    public WorkerResources findWorker(final String workerName) {
        return workers.get(workerName);
    }
}