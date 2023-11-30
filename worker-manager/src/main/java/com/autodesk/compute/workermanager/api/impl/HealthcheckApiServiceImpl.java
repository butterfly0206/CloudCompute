package com.autodesk.compute.workermanager.api.impl;

import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.model.HealthCheckResponse;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.discovery.SnsSubscriptionManager;
import com.autodesk.compute.workermanager.api.HealthcheckApiService;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.time.Instant;

@RequestScoped
@Slf4j
@Default
public class HealthcheckApiServiceImpl implements HealthcheckApiService {
    // Needed for CDI (@Inject) to work
    private final ComputeConfig computeConfig;
    private final SnsSubscriptionManager subscriptionManager;

    @Inject
    public HealthcheckApiServiceImpl() {
        this(ComputeConfig.getInstance(), new SnsSubscriptionManager());
    }

    public HealthcheckApiServiceImpl(final ComputeConfig argComputeConfig, final SnsSubscriptionManager subscriptionManager) {
        this.computeConfig = argComputeConfig;
        this.subscriptionManager = subscriptionManager;
    }


    @Override
    public Response healthcheckGet(final String vaultToken, final String vaultUser, final SecurityContext securityContext)
            throws WorkerManagerException {

        //Once ALB becomes healthy, subscribe to SNS
        subscribeToSNSTopic();

        if (ComputeStringOps.isNullOrEmpty(vaultToken)) {
            return Response.ok().build();
        } else {
            try {
                final String revision = computeConfig.getRevision();
                final HealthCheckResponse response = new HealthCheckResponse();
                response.setOverall(HealthCheckResponse.OverallEnum.HEALTHY);
                response.setScanTime(Instant.now().toString());
                response.setRevision(revision);
                response.setPortfolioVersion(computeConfig.getPortfolioVersion());
                return Response.ok().entity(response).build();
            } catch (final Exception e) {
                log.error("Health check failed!", e);
                throw new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, "Health check", e);
            }
        }
    }
    // refactor this dependency so that it can be mocked.
    public void subscribeToSNSTopic() {
        if (!ComputeConfig.isCosV3() && !subscriptionManager.isSubscribedToSNSTopic()) {
            subscriptionManager.subscribeToADFUpdateSNSTopic();
        }
    }
}
