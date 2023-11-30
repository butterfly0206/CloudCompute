package com.autodesk.compute.model;

import lombok.Builder;
import lombok.Data;
import lombok.With;

@Data
@Builder
@With
public class StateMachineParameters {
    private int jobTimeoutSeconds;
    private int heartbeatTimeoutSeconds;
    private int jobAttempts;
    private String stateMachineName;
    private String activityName;
    private String activityArn;
    private String snsEndpoint;
    private boolean isPollingStateMachine;
}
