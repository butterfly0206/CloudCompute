package com.autodesk.compute.workermanager.boot;

import com.autodesk.compute.api.AppUpdateApi;
import com.autodesk.compute.auth.basic.OptionalBasicAuthRequestFilter;
import com.autodesk.compute.common.ComputeResteasyJackson2Provider;
import com.autodesk.compute.common.RequestTraceFilter;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.exception.ComputeExceptionMapper;
import com.autodesk.compute.exception.NoClassDefFoundErrorExceptionMapper;
import com.autodesk.compute.logging.MDCRequestFilter;
import com.autodesk.compute.model.PathData;
import com.autodesk.compute.workermanager.api.*;
import com.google.common.collect.Sets;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.jboss.resteasy.plugins.interceptors.CorsFilter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

@ApplicationPath("/")
public class RestApplication extends Application {
    private final RequestTraceFilter requestTraceFilter;
    private final OptionalBasicAuthRequestFilter basicAuthRequestFilter;
    private final Predicate<PathData> includeRequestsThatMatchPredicate;
    public RestApplication() {
        // Exclude POST /heartbeat and GET /healthcheck; log everything else.
        includeRequestsThatMatchPredicate = (pathData ->
                !pathData.getUrlPath().contains("heartbeat") && !pathData.getUrlPath().contains("healthcheck"));
        requestTraceFilter = new RequestTraceFilter(includeRequestsThatMatchPredicate);
        basicAuthRequestFilter = new OptionalBasicAuthRequestFilter(ComputeConfig.getInstance());
    }

	@Override
    public Set<Class<?>> getClasses() {
        final Set<Class<?>> resources = new LinkedHashSet<>();
        resources.add(ComputeResteasyJackson2Provider.class);
        resources.add(CompleteApi.class);
        resources.add(PollApi.class);
        resources.add(ProgressApi.class);
        resources.add(HeartbeatApi.class);
        resources.add(TokenApi.class);
        resources.add(HealthcheckApi.class);
        resources.add(ComputeExceptionMapper.class);
        resources.add(NoClassDefFoundErrorExceptionMapper.class);
        resources.add(AppUpdateApi.class);
        resources.add(JobsApi.class);
        return resources;
    }
    @Override
    public Set<Object> getSingletons() {
        final CorsFilter corsFilter = new CorsFilter();
        corsFilter.getAllowedOrigins().add("*");
        corsFilter.setAllowedMethods("GET");
        return Sets.newHashSet(
            new MDCRequestFilter(),
                corsFilter,
                basicAuthRequestFilter,
                requestTraceFilter
        );
    }
}