package com.autodesk.compute.cosv2;

import com.autodesk.compute.model.cosv2.AppDefinition;
import lombok.Getter;
import lombok.NonNull;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Getter
public class NameAssigner {

    @NonNull
    private final String appName;
    private final String portfolioVersion;
    private final boolean isMultiVersion;
    private final String cloudosMoniker;

    public NameAssigner(@NonNull final String appName, final String portfolioVersion, final Boolean isMultiVersion, final String cloudosMoniker) {
        this.appName = appName;
        this.portfolioVersion = (portfolioVersion == null) ? AppDefinition.UNKNOWN_PORTFOLIO_VERSION : portfolioVersion;
        this.isMultiVersion = isMultiVersion;
        this.cloudosMoniker = cloudosMoniker;
    }

    /**
     * Returns the modified app name. The name contains the version name if multi-version deployment is enabled
     * Some resources do not like dots in the names, so we replace them with dashes.
     *
     * @return Modified application name
     */
    public String getVersionedAppName() {
        return getVersionedName(appName);
    }

    /**
     * Returns the modified name. The name contains the version name if multi-version deployment is enabled
     * Some resources do not like dots in the names, so we replace them with dashes.
     * This version is identical as getVersionedAppName except that it takes in the name. This makes it a generic
     * version which can be useful for situations like getting modified component names, etc.
     *
     * @return Modified application name
     */
    private String getVersionedName(final String name) {
        return isMultiVersion ? name + "-" + periodsToHyphens(portfolioVersion) : name;
    }

    /**
     * Returns the batch job definition name, as defied by terraform
     *
     * @param jobDefinitionName The name of the batch job definition, as seen by the user
     * @return the name of the batch job definition
     */
    public String getBatchJobDefinitionName(final String jobDefinitionName) {
        return getVersionedAppName() + "-" + jobDefinitionName;
    }

    /**
     * Returns the worker identity for a compute batch worker without version
     *
     * @param workerName name of the worker
     * @return the worker identity if compute specification is present, empty otherwise.
     */
    public String getUnversionedWorkerId(final String workerName) {
        return makeString(getAppName(), "-", workerName);
    }


    /**
     * Returns the real ecs service name, as defined by terraform
     *
     * @param componentName The name of the component, as seen by the user
     * @return the name of the ecs service
     */
    public String getEcsServiceName(final String componentName) {
        return getVersionedAppName() + "-" + componentName;
    }

    public String periodsToHyphens(final String name) {
        return name.replace(".", "-");
    }

}
