package com.autodesk.compute.jobmanager.boot;

import com.autodesk.compute.api.AppUpdateApi;
import com.autodesk.compute.auth.oauth.OxygenAuthRequestFilter;
import com.autodesk.compute.auth.vault.OptionalVaultAuthRequestFilter;
import com.autodesk.compute.auth.vault.VaultAuthDriver;
import com.autodesk.compute.auth.vault.VaultAuthRequestFilter;
import com.autodesk.compute.common.ComputeResteasyJackson2Provider;
import com.autodesk.compute.common.RequestTraceFilter;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.exception.ComputeExceptionMapper;
import com.autodesk.compute.exception.NoClassDefFoundErrorExceptionMapper;
import com.autodesk.compute.jobmanager.api.HealthcheckApi;
import com.autodesk.compute.jobmanager.api.JobsApi;
import com.autodesk.compute.jobmanager.api.SearchApi;
import com.autodesk.compute.logging.MDCRequestFilter;
import com.autodesk.compute.model.PathData;
import com.google.common.collect.Sets;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.jboss.resteasy.plugins.interceptors.CorsFilter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

@ApplicationPath("/")
public class RestApplication extends Application {
    private final VaultAuthRequestFilter authRequestFilter;
    private final OptionalVaultAuthRequestFilter optionalAuthRequestFilter;
    private final OxygenAuthRequestFilter oxygenAuthRequestFilter;
    private final RequestTraceFilter requestTraceFilter;
    private final CorsFilter corsFilter;

    public RestApplication() {
        final ComputeConfig conf = ComputeConfig.getInstance();
        final VaultAuthDriver authDriver = new VaultAuthDriver(conf.getVaultAuthDriverConfiguration());
        authRequestFilter = new VaultAuthRequestFilter(authDriver);
        optionalAuthRequestFilter = new OptionalVaultAuthRequestFilter(authDriver);
        oxygenAuthRequestFilter = new OxygenAuthRequestFilter(conf.getOxygenReqFilterConfiguration());

        final Predicate<PathData> includeRequestsThatMatchPredicate;
        includeRequestsThatMatchPredicate = (pathData ->
                !pathData.getUrlPath().contains("healthcheck"));

        requestTraceFilter = new RequestTraceFilter(includeRequestsThatMatchPredicate);
        corsFilter = newCorsFilter();
    }

	@Override
    public Set<Class<?>> getClasses() {
        final Set<Class<?>> resources = new LinkedHashSet<>();
        resources.add(ComputeResteasyJackson2Provider.class);
        resources.add(JobsApi.class);
        resources.add(HealthcheckApi.class);
        resources.add(ComputeExceptionMapper.class);
        resources.add(NoClassDefFoundErrorExceptionMapper.class);
        resources.add(AppUpdateApi.class);
        resources.add(SearchApi.class);
        return resources;
    }

    public CorsFilter newCorsFilter() {
        final CorsFilter corsFilter = new CorsFilter();
        corsFilter.getAllowedOrigins().add("*");
        corsFilter.setAllowedMethods("GET");
        return corsFilter;
    }

    @Override
    public Set<Object> getSingletons() {
        return Sets.newHashSet(
            new MDCRequestFilter(),
            corsFilter,
            requestTraceFilter,
            authRequestFilter,
            optionalAuthRequestFilter,
            oxygenAuthRequestFilter);
    }
}
