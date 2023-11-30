package com.autodesk.compute.common;

import com.autodesk.compute.model.cosv2.ComputeSpecification;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static com.autodesk.compute.configuration.ComputeConstants.WorkerDefaults.*;

public class TestComputeSpecificationBuilder {

    @Test
    public void testDefaults() {
        MockitoAnnotations.openMocks(this);
        final ComputeSpecification specification = ComputeSpecification.ComputeSpecificationBuilder.buildWithDefaults();
        org.junit.Assert.assertEquals(DEFAULT_WORKER_HEARTBEAT_TIMEOUT_SECONDS, specification.getHeartbeatTimeoutSeconds());
        org.junit.Assert.assertEquals(DEFAULT_WORKER_JOB_TIMEOUT_SECONDS, specification.getJobTimeoutSeconds());
        org.junit.Assert.assertEquals(DEFAULT_WORKER_JOB_ATTEMPTS, specification.getJobAttempts());
    }
}
