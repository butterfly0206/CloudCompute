package com.autodesk.compute.jobmanager;

import com.autodesk.compute.api.AppUpdateApi;
import com.autodesk.compute.auth.oauth.OxygenAuthRequestFilter;
import com.autodesk.compute.auth.vault.OptionalVaultAuthRequestFilter;
import com.autodesk.compute.auth.vault.VaultAuthRequestFilter;
import com.autodesk.compute.common.ComputeResteasyJackson2Provider;
import com.autodesk.compute.common.RequestTraceFilter;
import com.autodesk.compute.exception.ComputeExceptionMapper;
import com.autodesk.compute.jobmanager.api.HealthcheckApi;
import com.autodesk.compute.jobmanager.api.JobsApi;
import com.autodesk.compute.jobmanager.api.SearchApi;
import com.autodesk.compute.jobmanager.boot.RestApplication;
import com.autodesk.compute.logging.MDCRequestFilter;
import com.autodesk.compute.test.categories.UnitTests;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.plugins.interceptors.CorsFilter;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


@Category(UnitTests.class)
@Slf4j
public class TestRestApplication {

    @Test
    public void testRestApplication() {
        final RestApplication app = new RestApplication();
        assertNotNull(app);

        final Set<Class<?>> classes = app.getClasses();
        assertNotNull(classes);
        assertTrue(classes.contains(ComputeResteasyJackson2Provider.class));
        assertTrue(classes.contains(JobsApi.class));
        assertTrue(classes.contains(HealthcheckApi.class));
        assertTrue(classes.contains(ComputeExceptionMapper.class));
        assertTrue(classes.contains(AppUpdateApi.class));
        assertTrue(classes.contains(SearchApi.class));

        final Set<Object> singletons = app.getSingletons();
        assertNotNull(singletons);
        assertTrue(singletons.stream().anyMatch(item -> item.getClass() == MDCRequestFilter.class));
        assertTrue(singletons.stream().anyMatch(item -> item.getClass() == CorsFilter.class));
        assertTrue(singletons.stream().anyMatch(item -> item.getClass() == RequestTraceFilter.class));
        assertTrue(singletons.stream().anyMatch(item -> item.getClass() == VaultAuthRequestFilter.class));
        assertTrue(singletons.stream().anyMatch(item -> item.getClass() == OptionalVaultAuthRequestFilter.class));
        assertTrue(singletons.stream().anyMatch(item -> item.getClass() == OxygenAuthRequestFilter.class));
    }
}