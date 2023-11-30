package com.autodesk.compute.jobmanager;

import com.amazonaws.SdkBaseException;
import com.autodesk.compute.common.model.HealthCheckResponse;
import com.autodesk.compute.discovery.SnsSubscriptionManager;
import com.autodesk.compute.discovery.WorkerResolver;
import com.autodesk.compute.jobmanager.api.impl.HealthcheckApiServiceImpl;
import com.autodesk.compute.jobmanager.util.JobManagerException;
import com.autodesk.compute.test.categories.UnitTests;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

@Category(UnitTests.class)
@Slf4j
public class TestHealthCheckApi {

    @Mock
    private WorkerResolver workerResolver;

    @Mock
    private SnsSubscriptionManager subscriptionManager;

    @Spy
    @InjectMocks
    HealthcheckApiServiceImpl healthCheckApi;

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
        Mockito.doReturn(true).when(subscriptionManager).subscribeToADFUpdateSNSTopic();
        Mockito.doReturn(new ArrayList<>()).when(workerResolver).getServices();
    }

    @Test
    public void testHealthCheckOk() throws JobManagerException {
        final HealthCheckResponse response = (HealthCheckResponse) healthCheckApi.healthcheckGet("token", "user", null).getEntity();
        assertEquals(HealthCheckResponse.OverallEnum.HEALTHY, response.getOverall());
    }

    @Test
    public void testHealthCheckEmptyToken() throws JobManagerException {
        assertEquals(200, healthCheckApi.healthcheckGet("a-vault-token", "user", null).getStatus());
    }

    @Test
    public void testHealthCheckDegraded() throws JobManagerException {
        Mockito.doThrow(new SdkBaseException("error")).when(workerResolver).getServices();
        final HealthCheckResponse response = (HealthCheckResponse) healthCheckApi.healthcheckGet("token", "user", null).getEntity();
        assertEquals(HealthCheckResponse.OverallEnum.DEGRADED, response.getOverall());
    }

}
