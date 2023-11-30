package com.autodesk.compute.workermanager;

import com.autodesk.compute.common.model.HealthCheckResponse;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.workermanager.api.impl.HealthcheckApiServiceImpl;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

@Category(UnitTests.class)
@Slf4j
public class TestHealthCheckApi {

    @Mock
    ComputeConfig computeConfig;

    @Mock
    DynamoDBJobClient dynamoDB;

    @Spy
    @InjectMocks
    HealthcheckApiServiceImpl healthCheckApi;

    @BeforeClass
    public static void beforeClass() {
    }

    @Test
    public void testHealthcheckOk() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(healthCheckApi).subscribeToSNSTopic();
        Mockito.doReturn(new BigDecimal(5)).when(dynamoDB).getJobCount();
        final HealthCheckResponse response = (HealthCheckResponse) healthCheckApi.healthcheckGet("token", "user", null).getEntity();
        assertEquals(HealthCheckResponse.OverallEnum.HEALTHY, response.getOverall());
    }

    @Test
    public void testHealthcheckEmptyToken() throws WorkerManagerException, ComputeException {
        MockitoAnnotations.openMocks(this);
        Mockito.doNothing().when(healthCheckApi).subscribeToSNSTopic();
        Mockito.doReturn(new BigDecimal(5)).when(dynamoDB).getJobCount();
        assertEquals(200, healthCheckApi.healthcheckGet("a-vault-token", "user", null).getStatus());
    }
}
