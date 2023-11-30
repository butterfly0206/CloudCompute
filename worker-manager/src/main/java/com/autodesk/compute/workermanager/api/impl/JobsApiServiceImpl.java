package com.autodesk.compute.workermanager.api.impl;

import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.model.Job;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.JobCache;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.util.JsonHelper;
import com.autodesk.compute.workermanager.api.JobsApiService;
import com.autodesk.compute.workermanager.api.NotFoundException;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import com.google.common.cache.LoadingCache;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static java.util.Optional.ofNullable;

@Slf4j
@RequestScoped
@Default
public class JobsApiServiceImpl implements JobsApiService {
    public static final String GET_JOB = "getJob";
    private final LoadingCache<String, JobDBRecord> jobCache;

    // Needed for @Inject to work
    @Inject
    public JobsApiServiceImpl() {
        this(JobCache.getInstance().getJobCache());
    }

    public JobsApiServiceImpl(final LoadingCache<String, JobDBRecord> jobCache) {
        this.jobCache = jobCache;
    }

    @Override
    public Response getJob(final String id, final SecurityContext securityContext) throws WorkerManagerException, NotFoundException {
        try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, GET_JOB)) {
            loader.withField(MDCFields.JOB_ID, id);
            if (ComputeStringOps.isNullOrEmpty(id))
                throw new WorkerManagerException(ComputeErrorCodes.BAD_REQUEST, "Job Id must be specified");

            final Job job = ofNullable(jobCache.get(id))
                    .map(JsonHelper::convertToJob)
                    .orElseThrow(() -> new WorkerManagerException(ComputeErrorCodes.NOT_FOUND, makeString("Job ", id, " was not found")));

            return Response.ok().entity(job).build();
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof ComputeException) {
                final ComputeException computeException = (ComputeException) e.getCause();
                if (computeException.getCode() == ComputeErrorCodes.NOT_FOUND)
                    return null;
                throw new WorkerManagerException(computeException.getCode(), e.getCause());
            } else {
                throw new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, makeString("Job ", id, " not found"), e.getCause());
            }
        }
    }
}
