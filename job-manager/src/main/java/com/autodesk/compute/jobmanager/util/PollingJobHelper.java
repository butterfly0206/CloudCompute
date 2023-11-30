package com.autodesk.compute.jobmanager.util;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;
import com.amazonaws.services.stepfunctions.model.*;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;

@AllArgsConstructor
@Slf4j
public class PollingJobHelper {
    private static final String FAILED_SFN_DELETE = "Failed to delete SFN execution";

    private final AWSStepFunctions sfn;

    @Inject
    public PollingJobHelper() {
        this(makeStandardClient(AWSStepFunctionsClientBuilder.standard()));
    }

    public void stopSFNExecutionAndIgnoreIfMissing(@NonNull final String executionArn) throws JobManagerException {
        final StopExecutionResult stopExecutionResult;
        try {
            log.info("stopSFNExecution called with Arn: {}", executionArn);
            final StopExecutionRequest stopExecutionRequest = new StopExecutionRequest().withExecutionArn(executionArn);
            stopExecutionResult = sfn.stopExecution(stopExecutionRequest);
            log.info("stopSFNExecution: StopExecutionResult: {}", stopExecutionResult);
        } catch (final ExecutionLimitExceededException e) {
            log.error(FAILED_SFN_DELETE, e);
            throw new JobManagerException(ComputeErrorCodes.AWS_INTERNAL_ERROR, e);
        } catch (final ExecutionDoesNotExistException e) {
            log.warn("StopSFNExecution: The execution {} to be removed does not exist.", executionArn);
        } catch (final AWSStepFunctionsException e) {
            log.error(FAILED_SFN_DELETE, e);
            throw new JobManagerException(ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.NOT_FOUND,
                    e);
        } catch (final Exception e) {
            log.error(FAILED_SFN_DELETE, e);
            throw new JobManagerException(
                    ThrottleCheck.isThrottlingException(e) ? ComputeErrorCodes.TOO_MANY_REQUESTS : ComputeErrorCodes.SERVER_UNEXPECTED, e);
        }
    }

}
