package com.autodesk.compute.common;

import lombok.NonNull;
import lombok.Value;

@Value
public class ServiceWithVersion {
    public static final String DEPLOYED = "deployed";

    private String serviceName;
    private String portfolioVersion;

    public static ServiceWithVersion fromKey(@NonNull final String cacheKey) {
        final String[] pieces = cacheKey.split("\\|");
        return new ServiceWithVersion(pieces[0], pieces.length > 1 ? pieces[1] : null);
    }

    public static boolean isCurrentVersion(final String portfolioVersion) {
        return ComputeStringOps.isNullOrEmpty(portfolioVersion) ||
                portfolioVersion.equalsIgnoreCase(DEPLOYED);
    }

    public static String makeKey(@NonNull final String service, final String portfolioVersion) {
        return isCurrentVersion(portfolioVersion) ? service :
                ComputeStringOps.makeString(service, "|", portfolioVersion);
    }

    public static String makeKey(@NonNull final String service) {
        return service;
    }

    public static String makeKey(final ServiceWithVersion name) {
        return makeKey(name.getServiceName(), name.getPortfolioVersion());
    }
}

