package com.autodesk.compute.jobmanager.api.impl;

import com.amazonaws.SdkBaseException;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.model.HealthCheckResponse;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.discovery.SnsSubscriptionManager;
import com.autodesk.compute.discovery.WorkerResolver;
import com.autodesk.compute.jobmanager.api.HealthcheckApiService;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@RequestScoped
@Slf4j
@Default
public class HealthcheckApiServiceImpl implements HealthcheckApiService {
    private final SnsSubscriptionManager subscriptionManager;
    private final WorkerResolver workerResolver;

    @Inject
    public HealthcheckApiServiceImpl() {
        this(new SnsSubscriptionManager(), WorkerResolver.getInstance());
    }

    public HealthcheckApiServiceImpl(final SnsSubscriptionManager subscriptionManager, final WorkerResolver workerResolver) {
        this.subscriptionManager = subscriptionManager;
        this.workerResolver = workerResolver;
    }

    @Override
    public Response healthcheckGet(final String vaultToken, final String vaultUser, final SecurityContext securityContext) {
        try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "healthcheck")) {
            //Once ALB becomes healthy, subscribe to SNS
            if (!ComputeConfig.isCosV3() && !subscriptionManager.isSubscribedToSNSTopic()) {
                subscriptionManager.subscribeToADFUpdateSNSTopic();
            }
            if (ComputeStringOps.isNullOrEmpty(vaultToken)) {
                return Response.ok().build();
            } else {
                String revision = "LOCAL";
                String portfolioVersion = "deployed";
                final HealthCheckResponse response = new HealthCheckResponse();
                try {
                    final ComputeConfig cfg = ComputeConfig.getInstance();
                    revision = cfg.getRevision();
                    portfolioVersion = cfg.getPortfolioVersion();
                } catch (final RuntimeException e) {
                    // Skip and do nothing
                }
                final Instant startTime = Instant.now();
                response.setPortfolioVersion(portfolioVersion);
                response.setRevision(revision);
                response.setScanTime(startTime.toString());


                try {
                    workerResolver.getServices();
                } catch (final SdkBaseException e) {
                    log.error("Got exception, reporting compute health as DEGRADED", e);
                    response.setOverall(HealthCheckResponse.OverallEnum.DEGRADED);
                    return Response.ok().entity(response).build();
                }
                response.setOverall(HealthCheckResponse.OverallEnum.HEALTHY);
                return Response.ok().entity(response).build();
            }
        }
    }
}
