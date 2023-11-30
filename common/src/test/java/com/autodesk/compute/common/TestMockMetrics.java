package com.autodesk.compute.common;

import com.autodesk.compute.configuration.ComputeConstants;
import com.google.common.collect.Sets;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.stream.Collectors;


public class TestMockMetrics {
    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void MockMetricsHasAllOfMetricsNonStaticPublicMembers() {
        final Method[] metricsMethods = Metrics.class.getMethods();
        final Method[] mockMethods = MockMetrics.class.getMethods();

        final SortedSet<String> metricsPublicInstanceMethods =
                Sets.newTreeSet(
                     Arrays.stream(metricsMethods)
                             .map(Method::toGenericString)
                             .filter(signature -> !signature.contains("public static"))
                             .map(signature -> signature.replace(Metrics.class.getCanonicalName(), ""))
                        .collect(Collectors.toList())
                );
        final SortedSet<String> mockMetricsPublicInstanceMethods =
                Sets.newTreeSet(
                        Arrays.stream(mockMethods)
                                .map(Method::toGenericString)
                                .filter(signature -> !signature.contains("public static"))
                                .map(signature -> signature.replace(MockMetrics.class.getCanonicalName(), ""))
                        .collect(Collectors.toList())
                );
        final Sets.SetView<String> setIntersection = Sets.intersection(
                metricsPublicInstanceMethods,
                mockMetricsPublicInstanceMethods);
        Assert.assertEquals(setIntersection.size(), metricsPublicInstanceMethods.size());
    }

    @Test
    public void GetMockImplementationWhenConfigured()
    {
        environmentVariables.set(ComputeConstants.MetricsNames.USE_MOCK_ENV_VAR, "true");
        final Metrics configuredMetrics = Metrics.getUninitializedInstance();
        Assert.assertTrue(configuredMetrics instanceof MockMetrics);
    }

    @Test
    public void GetRealImplementationWhenConfigured()
    {
        environmentVariables.set(ComputeConstants.MetricsNames.USE_MOCK_ENV_VAR, "false");
        final Metrics configuredMetrics = Metrics.getUninitializedInstance();
        Assert.assertTrue(configuredMetrics.getClass().getCanonicalName().equals(Metrics.class.getCanonicalName()));
    }

}
