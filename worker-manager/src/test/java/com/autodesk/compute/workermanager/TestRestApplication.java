package com.autodesk.compute.workermanager;

import com.autodesk.compute.api.AppUpdateApi;
import com.autodesk.compute.auth.basic.OptionalBasicAuthRequestFilter;
import com.autodesk.compute.common.ComputeResteasyJackson2Provider;
import com.autodesk.compute.common.RequestTraceFilter;
import com.autodesk.compute.exception.ComputeExceptionMapper;
import com.autodesk.compute.exception.NoClassDefFoundErrorExceptionMapper;
import com.autodesk.compute.logging.MDCRequestFilter;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.workermanager.api.*;
import com.autodesk.compute.workermanager.boot.RestApplication;
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
        assertTrue(classes.contains(CompleteApi.class));
        assertTrue(classes.contains(PollApi.class));
        assertTrue(classes.contains(ProgressApi.class));
        assertTrue(classes.contains(HeartbeatApi.class));
        assertTrue(classes.contains(TokenApi.class));
        assertTrue(classes.contains(HealthcheckApi.class));
        assertTrue(classes.contains(ComputeExceptionMapper.class));
        assertTrue(classes.contains(NoClassDefFoundErrorExceptionMapper.class));
        assertTrue(classes.contains(AppUpdateApi.class));
        assertTrue(classes.contains(JobsApi.class));

        final Set<Object> singletons = app.getSingletons();
        assertNotNull(singletons);
        assertTrue(singletons.stream().anyMatch(item -> item.getClass() == MDCRequestFilter.class));
        assertTrue(singletons.stream().anyMatch(item -> item.getClass() == CorsFilter.class));
        assertTrue(singletons.stream().anyMatch(item -> item.getClass() == RequestTraceFilter.class));
        assertTrue(singletons.stream().anyMatch(item -> item.getClass() == OptionalBasicAuthRequestFilter.class));
    }
}