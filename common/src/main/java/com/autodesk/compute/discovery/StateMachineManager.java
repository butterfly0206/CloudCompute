package com.autodesk.compute.discovery;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder;
import com.amazonaws.services.stepfunctions.builder.ErrorCodes;
import com.amazonaws.services.stepfunctions.builder.StateMachine;
import com.amazonaws.services.stepfunctions.builder.states.TaskState;
import com.amazonaws.services.stepfunctions.model.*;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.model.StateMachineParameters;
import com.autodesk.compute.model.cosv2.ComputeSpecification;
import com.google.common.base.MoreObjects;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.util.Optional;

import static com.amazonaws.services.stepfunctions.builder.StepFunctionBuilder.*;
import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.common.ComputeStringOps.makeString;

/**
 * ResourceManager schedules adf discovery at given period and creates resources when new adf
 * gets discovered and removes them when adf is deleted.
 */
@Slf4j
public class StateMachineManager {

    private final AWSStepFunctions sfn;
    private final ComputeSpecification defaultComputeSpecification = ComputeSpecification.ComputeSpecificationBuilder.buildWithDefaults();

    @Inject
    public StateMachineManager() {
        this(makeStandardClient(AWSStepFunctionsClientBuilder.standard()));
    }

    public StateMachineManager(final AWSStepFunctions sfn) {
        this.sfn = sfn;
    }

    public ComputeConfig getComputeConfig() {
        return ComputeConfig.getInstance();
    }

    /**
     * Creates step function activity with given name
     *
     * @param activityName name of the step function activity
     * @return activity ARN
     */
    public String createActivity(@NonNull final String activityName) {
        final CreateActivityResult activity = sfn.createActivity(new CreateActivityRequest().withName(activityName));
        return activity.getActivityArn();
    }

    // Jobs get extra retries for polling workers
    public int actualJobRetries(final boolean isPollingWorker, final int requestedJobRetries) {
        return isPollingWorker ? (requestedJobRetries + 3) : requestedJobRetries;   //NOPMD
    }

    public TaskState.Builder getWorkerActivityState(@NonNull final String activityArn, final int timeout, final int heartbeatTimeout, final int jobAttempts) {
        return taskState()
                .timeoutSeconds(timeout)
                .resource(activityArn)
                .heartbeatSeconds(heartbeatTimeout)
                .retrier(retrier()
                        .errorEquals(ErrorCodes.TIMEOUT)
                        .intervalSeconds(1)
                        .backoffRate(2.0)
                        .maxAttempts(actualJobRetries(true, jobAttempts))) // 3 additional attempts for lost jobs during poll
                .resultPath("$.output") //NOSONAR
                .transition(end());
    }

    public StateMachine getStateMachine(@NonNull final ServiceResources.WorkerResources workerResources, @NonNull final String activityArn) {
        final StateMachineParameters parameters = getStateMachineParameters(workerResources, activityArn);
        //TODO: Regretfully (and temporarily) we're going to allow batch polling on SI jobs if noBatch=true.
        // So create the state machine to handle that case.
        return sharedStateMachine(parameters).build();
    }

    public StateMachine.Builder sharedStateMachine(final StateMachineParameters stateMachineParameters)
    {
        return stateMachine()
                .comment(makeString("State machine for service ", stateMachineParameters.getStateMachineName()))
                .startAt(stateMachineParameters.getActivityName())
                // The timeout time is: one job timeout duration for the first attempt, plus one job duration time per retry.
                // And since we add additional retries for polling workers, include them in the timeout.
                .timeoutSeconds(
                        (actualJobRetries(stateMachineParameters.isPollingStateMachine(), stateMachineParameters.getJobAttempts()) + 1) *
                                Math.toIntExact(stateMachineParameters.getJobTimeoutSeconds()))
                .state(stateMachineParameters.getActivityName(), getWorkerActivityState(
                        stateMachineParameters.getActivityArn(),
                        stateMachineParameters.getJobTimeoutSeconds(),
                        stateMachineParameters.getHeartbeatTimeoutSeconds(),
                        stateMachineParameters.getJobAttempts()));
    }

    public StateMachineParameters getStateMachineParameters(final ServiceResources.WorkerResources workerResources, final String activityArn) {
        final boolean isBatchWorker = workerResources instanceof ServiceResources.BatchWorkerResources;
        final ComputeSpecification computeSpec = workerResources.getComputeSpecification();

        final StateMachineParameters.StateMachineParametersBuilder parameters = StateMachineParameters.builder()
                .jobAttempts(MoreObjects.firstNonNull(computeSpec.getJobAttempts(), defaultComputeSpecification.getJobAttempts()))
                .jobTimeoutSeconds(MoreObjects.firstNonNull(computeSpec.getJobTimeoutSeconds(), defaultComputeSpecification.getJobTimeoutSeconds()))
                .heartbeatTimeoutSeconds(MoreObjects.firstNonNull(computeSpec.getHeartbeatTimeoutSeconds(), defaultComputeSpecification.getHeartbeatTimeoutSeconds()))
                .activityName(workerResources.activityName)
                .activityArn(activityArn)
                .stateMachineName(workerResources.getStateMachineName())
                .isPollingStateMachine(!isBatchWorker);

        return parameters.build();
    }

    /**
     * Creates state machine
     *
     * @param workerResources resource for a service
     * @param activityArn activity to use for one activity state machine. This activity is both start and end ATM.
     * @param roleArn ARN of a role to use for step function.
     */
    public String createStateMachine(@NonNull final ServiceResources.WorkerResources workerResources,
                                     @NonNull final String activityArn, @NonNull final String roleArn) {
        final StateMachine sm = getStateMachine(workerResources, activityArn);
        // Null is now possible.
        if (sm == null)
            return null;
        try {
            final boolean updateInsteadOfCreate = doesStateMachineExist(workerResources.getStateMachineArn());

            if (updateInsteadOfCreate) {
                log.info("state machine already exists {}, updating...", workerResources.getStateMachineName());
                sfn.updateStateMachine(new UpdateStateMachineRequest()
                        .withStateMachineArn(workerResources.getStateMachineArn())
                        .withDefinition(sm.toJson())
                        .withRoleArn(roleArn));
                workerResources.setStateMachine(sm);
                return workerResources.getStateMachineArn();
            } else {
                log.info("creating state machine with role {} and activity {}", roleArn, workerResources.getActivityArn());
                final CreateStateMachineRequest request = new CreateStateMachineRequest()
                        .withName(workerResources.stateMachineName)
                        .withDefinition(sm)
                        .withRoleArn(roleArn);
                final CreateStateMachineResult result = sfn.createStateMachine(request);
                log.info("state machine arn {} created", result.getStateMachineArn());
                workerResources.setStateMachine(sm);
                return result.getStateMachineArn();
            }
        } catch (final Exception e) {
            log.error(makeString("Error creating state machine for ", workerResources.stateMachineName), e);
        }
        return null;
    }

    public boolean doesStateMachineExist(@NonNull final String stateMachineArn) {
        try {
            final DescribeStateMachineRequest describeRequest = new DescribeStateMachineRequest()
                    .withStateMachineArn(stateMachineArn);
            sfn.describeStateMachine(describeRequest);
            return true;
        } catch (final StateMachineDoesNotExistException e) {
            return false;
        }
    }

    /**
     * Deletes activity by name
     *
     * @param activityArn name of the activity to delete
     */
    public void deleteActivity(@NonNull final String activityArn) {
        sfn.deleteActivity(new DeleteActivityRequest().withActivityArn(activityArn));
    }

    /**
     * Deletes resources for a service
     *
     * @param serviceResources adf for which to delete resources
     */
    public void deleteResources(final Optional<ServiceResources> serviceResources) {
        if (!serviceResources.isPresent())
            return;
        for (final ServiceResources.WorkerResources worker : serviceResources.get().getWorkers().values()) {
            final String smArn = worker.getStateMachineArn();
            deleteStateMachine(smArn);
            final String activityArn = worker.getActivityArn();
            deleteActivity(activityArn);
        }
    }

    /**
     *  Deletes state machine by ARN
     *
     * @param stateMachineArn ARN of the state machine to delete
     */
    public void deleteStateMachine(@NonNull final String stateMachineArn) {
        sfn.deleteStateMachine(new DeleteStateMachineRequest().withStateMachineArn(stateMachineArn));
    }
}