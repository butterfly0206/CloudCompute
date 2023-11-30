package com.autodesk.compute.workermanager.util;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;
import com.amazonaws.services.stepfunctions.model.AWSStepFunctionsException;
import com.amazonaws.services.stepfunctions.model.SendTaskHeartbeatRequest;
import com.amazonaws.services.stepfunctions.model.TaskTimedOutException;
import com.amazonaws.util.StringUtils;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.HeartbeatResponse;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobCache;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.util.AuthCheckHelper;
import com.google.common.cache.LoadingCache;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Slf4j
public class HeartbeatHandler {
    private static final String POST_HEARTBEAT = "postHeartbeat";
    private final AWSStepFunctions sfn;

    private final LoadingCache<String, JobDBRecord> jobCache;
    private final DynamoDBJobClient dynamoDBJobClient;
    private final Response.Status httpStatusOnTimeout;
    private final Metrics metrics;


    // For heartbeats, the http status was 200. For progress API calls, the http status was 408.
    // So we'll share the code here - but they really should do the same thing.
    // TODO: revisit this, validate with the clients that we could change the behavior to 408 in all cases, then do so.
    @Inject
    public HeartbeatHandler() {
        this(Response.Status.OK);
    }

    public HeartbeatHandler(final Response.Status httpStatusOnTimeout) {
        this(new DynamoDBJobClient(ComputeConfig.getInstance()),
                makeStandardClient(AWSStepFunctionsClientBuilder.standard()),
                httpStatusOnTimeout, Metrics.getInstance(),
                JobCache.getInstance().getJobCache());
    }

    public HeartbeatHandler(final DynamoDBJobClient dynamoDBJobClient, final AWSStepFunctions tempSfn, final Response.Status httpStatusOnTimeout, final Metrics metrics, final LoadingCache<String, JobDBRecord> jobCache) {
        this.dynamoDBJobClient = dynamoDBJobClient;
        this.sfn = tempSfn;
        this.httpStatusOnTimeout = Optional.ofNullable(httpStatusOnTimeout).orElse(Response.Status.OK);
        this.metrics = metrics;
        this.jobCache = jobCache;
    }

    public Response processHeartbeat(final String jobId, final String jobSecret, final SecurityContext securityContext) throws WorkerManagerException {
        return processHeartbeatInternal(null, jobId, jobSecret, securityContext);
    }

    public Response processHeartbeat(final JobDBRecord jobDBRecord, final String jobSecret, final SecurityContext securityContext) throws WorkerManagerException {
        return processHeartbeatInternal(jobDBRecord, null, jobSecret, securityContext);
    }

    private Response processHeartbeatInternal(final JobDBRecord tmpJobDBRecord, final String tmpJobId, final String jobSecret, final SecurityContext securityContext) throws WorkerManagerException {
        // validate params
        if (tmpJobDBRecord == null && ComputeStringOps.isNullOrEmpty(tmpJobId)) {
            throw new WorkerManagerException(ComputeErrorCodes.SERVER, "processHeartbeatInternal: bad parameters, either job record or job id must not be null");
        }

        String jobId = tmpJobId;
        JobDBRecord jobDBRecord = tmpJobDBRecord;
        try {
            if (jobDBRecord == null) {
                jobDBRecord = jobCache.get(jobId); // does not require current record
            } else {
                jobId = jobDBRecord.getJobId();
            }
            try (final MDCLoader loader = MDCLoader.forField(MDCFields.JOB_ID, jobId)
                    .withField(MDCFields.OPERATION, POST_HEARTBEAT)) {

                if (jobId == null) {
                    throw new WorkerManagerException(ComputeErrorCodes.BAD_REQUEST, makeString("Job record has no job ID"));
                }

                if (jobDBRecord == null) {
                    throw new WorkerManagerException(ComputeErrorCodes.NOT_FOUND, makeString("Job ID ", jobId, " not found"));
                }

                final String moniker = Objects.isNull(jobDBRecord.getService()) ? "" : jobDBRecord.getService();
                loader.withMoniker(moniker);
                loader.withField(MDCFields.WORKER, jobDBRecord.getWorker());
                loader.withField(MDCFields.PORTFOLIO_VERSION, jobDBRecord.getPortfolioVersion());
                /*
                 * Checks if the security context provided, is authorized to perform Heartbeat operation on this particular job.
                 */

                if (!AuthCheckHelper.isServiceAuthorized(moniker, securityContext)) {
                    throw new WorkerManagerException(ComputeErrorCodes.NOT_AUTHORIZED, makeString("This service is not authorized to make progress changes for the Job {}", jobId));
                }

                if (StringUtils.isNullOrEmpty(jobSecret)) {
                    log.error(makeString("Received heartbeat without job secret for job ", jobId));
                    throw new WorkerManagerException(ComputeErrorCodes.BAD_REQUEST, "Can not send heartbeat without a valid job secret.");
                } else {
                    // TODO: DB-flavored heartbeat needs to validate the job secret also
                    metrics.reportHeartbeatSent();

                    if (jobDBRecord.isBatch()) {
                        dynamoDBJobClient.updateHeartbeat(jobId);
                    } else {
                        sfn.sendTaskHeartbeat(new SendTaskHeartbeatRequest()
                                .withTaskToken(TaskTokenUtils.getTaskTokenFromJobSecret(jobSecret)));
                    }
                }
            }

            // If we are not canceled, we actually have nothing to respond (no sense sending 'canceled:true')
            return Response.ok().build();
        } catch (final ComputeException e) {
            // Can be caused by an invalid job secret, or by heartbeating after a job has been canceled.
            // .. or by throttling
            if (ThrottleCheck.isThrottlingException(e)) {
                throw new WorkerManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, e);
            }
            throw new WorkerManagerException(e);
        } catch (final TaskTimedOutException e) {
            // Trying to send a heartbeat to a canceled job yields TaskTimedOutException
            // It does this whether or not it really timed out before the cancellation
            final HeartbeatResponse heartbeatResponse = new HeartbeatResponse();
            heartbeatResponse.setCanceled(true);  // Respond to our worker that this job is canceled
            log.warn(makeString("Worker for job id ", jobId, " tried to send a heartbeat, but the job was cancelled"));
            return Response.status(httpStatusOnTimeout).entity(heartbeatResponse).build();
        } catch (final AWSStepFunctionsException e) { // TaskDoesNotExistException | InvalidTokenException
            log.error(makeString("JobId ", jobId, " failed to send heartbeat to SFN task"), e);
            throw new WorkerManagerException(ComputeErrorCodes.NOT_FOUND, e);
        } catch (final WorkerManagerException e) {
            // rethrow any WorkerManagerException, skipping unnecessary throttling check below
            throw e;
        } catch (final ExecutionException e) {
            if (e.getCause() instanceof ComputeException) {
                final ComputeException computeException = (ComputeException) e.getCause();
                throw new WorkerManagerException(computeException);
            } else {
                throw new WorkerManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, e.getCause());
            }
        } catch (final Exception e) {
            // Check specifically for throttling exception types, wrap if so, rethrow otherwise
            if (ThrottleCheck.isThrottlingException(e)) {
                throw new WorkerManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, e);
            }
            throw e;
        }
    }

}
