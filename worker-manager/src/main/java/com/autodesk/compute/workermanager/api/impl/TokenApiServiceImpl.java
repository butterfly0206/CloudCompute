package com.autodesk.compute.workermanager.api.impl;

import com.autodesk.compute.common.ComputeUtils;
import com.autodesk.compute.common.OxygenClient;
import com.autodesk.compute.common.model.AcquireTokenArguments;
import com.autodesk.compute.common.model.NoTokenResponse;
import com.autodesk.compute.common.model.TokenResponse;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobCache;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.exception.IComputeException;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.workermanager.api.TokenApiService;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.google.common.cache.LoadingCache;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;

import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.common.ComputeUtils.guessMonikerMDCfromSecurityContext;
import static com.google.common.base.MoreObjects.firstNonNull;

@Slf4j
@RequestScoped
@Default
public class TokenApiServiceImpl implements TokenApiService {
    private final LoadingCache<String, JobDBRecord> jobCache;
    private final DynamoDBJobClient dynamoDBClient;

    private final OxygenClient oxygenClient;

    @Inject
    public TokenApiServiceImpl() {
        this(ComputeConfig.getInstance());
    }

    public TokenApiServiceImpl(final ComputeConfig computeConfig) {
        this(computeConfig,
                new OxygenClient(computeConfig.getOxygenUrl(),
                        computeConfig.getOxygenClientId(),
                        computeConfig.getOxygenClientSecret(),
                        ComputeUtils.TimeoutSettings.SLOW),
                new DynamoDBJobClient(computeConfig),
                JobCache.getInstance().getJobCache());
    }

    public TokenApiServiceImpl(final ComputeConfig computeConfig,
                               final OxygenClient oxygenClient,
                               final DynamoDBJobClient dynamoDBClient,
                               final LoadingCache<String, JobDBRecord> jobCache) {
        this.oxygenClient = oxygenClient;
        this.dynamoDBClient = dynamoDBClient;
        this.jobCache = jobCache;
    }

    @Override
    public Response getToken(final String jobID, final String jobSecret, final Boolean refresh, final SecurityContext securityContext)
            throws WorkerManagerException {
        try (final MDCLoader loader = MDCLoader.forMoniker(guessMonikerMDCfromSecurityContext(securityContext))
                .withField(MDCFields.OPERATION, "getToken")
                .withField(MDCFields.JOB_ID, jobID)) {
            final AcquireTokenArguments acquireTokenArguments = new AcquireTokenArguments();
            acquireTokenArguments.setJobId(jobID);
            acquireTokenArguments.setJobSecret(jobSecret);
            acquireTokenArguments.setRefresh(refresh);
            return acquireToken(acquireTokenArguments);
        } catch (final ComputeException e) {
            throw new WorkerManagerException(e);
        }
    }

    @Override
    public Response acquireToken(final AcquireTokenArguments acquireTokenArguments, final SecurityContext securityContext)
            throws WorkerManagerException {
        try (final MDCLoader loader = MDCLoader.forMoniker(guessMonikerMDCfromSecurityContext(securityContext))
                .withField(MDCFields.JOB_ID, acquireTokenArguments.getJobId())
                .withField(MDCFields.OPERATION, "acquireToken")) {
            final JobDBRecord record = getJobFromCache(acquireTokenArguments.getJobId(), false);
            if (record == null)
                return return404Response();
            loader.withMoniker(record.getService());
            loader.withField(MDCFields.WORKER, record.getWorker());
            loader.withField(MDCFields.PORTFOLIO_VERSION, record.getPortfolioVersion());

            return acquireToken(acquireTokenArguments);
        } catch (final ComputeException e) {
            throw new WorkerManagerException(e);
        }
    }

    public Response acquireToken(final AcquireTokenArguments acquireTokenArguments) throws ComputeException, WorkerManagerException {

        // extend token is not supported by FedRAMP end-points due to compliance reason,
        // disable this for FedRAMP until/IF it becomes available
        // https://autodesk.slack.com/archives/C075EFGET/p1649874213798149
        if (ComputeConfig.getInstance().isFedRampEnvironment() && Boolean.TRUE.equals(acquireTokenArguments.getRefresh()))
            throw new WorkerManagerException(ComputeErrorCodes.NOT_ALLOWED, "Refreshing token is not supported yet.");

        // TODO: Absentis jobSecret sanatio
        final JobDBRecord record = getJobFromCache(acquireTokenArguments.getJobId(), false);
        final JobStatus currentStatus = JobStatus.valueOf(record.getStatus());

        if (!currentStatus.isActiveState()) {
            throw new WorkerManagerException(ComputeErrorCodes.NOT_ALLOWED, "Job is not active");
        }
        String token = record.getOxygenToken();

        if (token == null)
            return return404Response();

        final boolean refresh = firstNonNull(acquireTokenArguments.getRefresh(), false);

        if (refresh) {
            token = oxygenClient.extendToken(token);
            dynamoDBClient.updateToken(acquireTokenArguments.getJobId(), token);
            jobCache.invalidate(acquireTokenArguments.getJobId());
        }
        final TokenResponse response = new TokenResponse();
        response.setToken(token);
        return Response.ok().entity(response).build();
    }

    private Response return404Response() {
        final NoTokenResponse response = new NoTokenResponse();
        response.setMessage("No token was provided when the job was created");
        return Response.status(Response.Status.NOT_FOUND).entity(response).build();
    }

    // Public so it can be mocked
    public JobDBRecord getJobFromCache(final String id, final boolean refresh) throws WorkerManagerException {
        //get job or exception
        final JobDBRecord job;
        try {
            if (refresh) {
                jobCache.refresh(id);
            }
            job = jobCache.get(id);
        } catch (final ExecutionException e) {
            // From here, we're choosing to consciously hide dynamodb errors from the users.
            // We're making the choice so that the SLO for this API is better than it would be
            // if the 5xx response is sent.
            // Instead, we just return 404, and log.error here.
            if (e.getCause() instanceof IComputeException && ((IComputeException) e.getCause()).getCode() == ComputeErrorCodes.NOT_FOUND)
                return null;
            throw new WorkerManagerException(ComputeErrorCodes.SERVICE_UNAVAILABLE, e.getCause());
        }
        return job;
    }

}
