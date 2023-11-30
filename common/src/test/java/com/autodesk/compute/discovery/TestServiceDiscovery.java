package com.autodesk.compute.discovery;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.Services;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.autodesk.compute.model.cosv2.ApplicationDefinitions;
import com.autodesk.compute.model.cosv2.BatchJobDefinition;
import com.autodesk.compute.model.cosv2.ComputeSpecification;
import com.autodesk.compute.test.Matchers;
import com.autodesk.compute.test.categories.UnitTests;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Multimap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.http.HttpResponse;
import java.util.*;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.common.ServiceWithVersion.fromKey;
import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Category(UnitTests.class)
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class TestServiceDiscovery {

    private static ServiceDiscoveryConfig sdConfig;
    private static Services adfs;
    private static LocalAppDefinitionsLoader appDefinitions;

    @Spy
    @InjectMocks
    private static ServiceDiscovery serviceDiscovery;

    @Mock
    private final HttpResponse<String> response = Mockito.mock(HttpResponse.class);

    @BeforeClass
    public static void beforeClass() {
        try {
            sdConfig = ServiceDiscoveryConfig.getInstance();
            appDefinitions = new LocalAppDefinitionsLoader();
            appDefinitions.loadTestAppDefinitions();
        } catch (final RuntimeException e) {
            log.error("Testing: Unexpected exception getting ServiceDiscoveryConfig: ", e);
            fail();
        }
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
    }

    private boolean expectFailGetComputeServices(final ServiceDiscovery discovery) {
        validateThrows(ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVICE_DISCOVERY_EXCEPTION),
                () -> discovery.getDeployedComputeServices());
        return true;
    }

    private boolean expectSucceedGetComputeServices(final ServiceDiscovery discovery) {
        try {
            adfs = discovery.getDeployedComputeServices();
            assertNotNull(adfs);
            return true;
        } catch (final Exception e) {
            log.error("Unexpected exception: ", e);
            fail();
            return false;
        }
    }

    @Test
    public void testInstantiation() {
        assertNotNull(sdConfig);
        assertNotNull(serviceDiscovery);
    }

    @Test
    public void testDeployedServices() {
        // Run this against the live Gatekeeper service
        final ServiceDiscovery localServiceDiscovery = new ServiceDiscovery();
        final boolean expectedResultReceived = expectSucceedGetComputeServices(localServiceDiscovery);
        assertTrue("GetComputeServices should have succeeded", expectedResultReceived);
        try {
            final Multimap<String, AppDefinition> deployedServices = adfs.getAppDefinitions();
            int deployedCount = 0;
            for (final Map.Entry<String, AppDefinition> i : deployedServices.entries() ) {
                log.info("Found service: {}", i.getKey());
                log.info("Found service name: {}", fromKey(i.getKey()).getServiceName());
                final Optional<AppDefinition> adf = localServiceDiscovery.getAppFromServiceDiscoveryOrder(fromKey(i.getKey()).getServiceName(), i.getValue().getPortfolioVersion());
                assertTrue(makeString("Expected to find ADF for ", i.getKey(),
                        " portfolio version ", i.getValue().getPortfolioVersion(),
                        " but got nothing instead"), adf.isPresent());
                deployedCount++;
            }
            assertTrue(deployedCount > 0);
        } catch (final ComputeException e) {
            log.error("Unexpected exception: ", e);
            fail();
        }
    }

    @Test
    @SneakyThrows
    public void testAnomalousAdfRequestEmptyData() {
        Mockito.doReturn(response).when(serviceDiscovery).getAllDeployedAdfs(any(ServiceDiscoveryConfig.CloudOSVersion.class));
        final boolean expectedResultReceived = expectFailGetComputeServices(TestServiceDiscovery.serviceDiscovery);
        assertTrue("GetComputeServices should have failed", expectedResultReceived);
    }

    public ApplicationDefinitions makeMockDefinitions() {
        return ApplicationDefinitions.builder().appDefinitions(appDefinitions.testAppDefinitions.values()).build();
    }

    public ApplicationDefinitions makeEmptyDefinitions() {
        return ApplicationDefinitions.builder().appDefinitions(new ArrayList<>()).build();
    }

    @Test
    @SneakyThrows
    public void testFailureFirstTimeFails() throws ComputeException {
        final ServiceDiscovery discovery = spy(new ServiceDiscovery());
        Mockito.doAnswer(invocation -> {
            throw new ComputeException(ComputeErrorCodes.BAD_REQUEST, "From test");
        }).when(discovery).getAllDeployedAdfs(any(ServiceDiscoveryConfig.CloudOSVersion.class));
        final boolean expectedResultReceived = expectFailGetComputeServices(discovery);
        assertTrue("GetComputeServices should have failed", expectedResultReceived);
    }

    @Test
    @SneakyThrows
    public void testSuccessThenFailureSucceeds() throws ComputeException {
        final ServiceDiscovery discovery = spy(new ServiceDiscovery());
        Mockito.doReturn(makeMockDefinitions()).when(discovery).parseGetAllDeployedAdfs(any(ServiceDiscoveryConfig.CloudOSVersion.class));
        boolean expectedResultReceived = expectSucceedGetComputeServices(discovery);
        assertTrue("GetComputeServices should have succeeded", expectedResultReceived);
        Mockito.doThrow(ComputeException.class).when(discovery).parseGetAllDeployedAdfs(any(ServiceDiscoveryConfig.CloudOSVersion.class));
        expectedResultReceived = expectSucceedGetComputeServices(discovery);
        assertTrue("GetComputeServices should have succeeded", expectedResultReceived);
    }

    @Test
    @SneakyThrows
    public void testAnomalousSelfAdfRequestComputeException() {
        Mockito.doReturn(response).when(serviceDiscovery).getAllDeployedAdfs(any(ServiceDiscoveryConfig.CloudOSVersion.class));
        final boolean expectedResultReceived = expectFailGetComputeServices(serviceDiscovery);
        assertTrue("GetComputeServices should have failed", expectedResultReceived);
    }

    @Test
    public void testAnomalousComputeADFCall() throws Exception {
        final Optional<AppDefinition> service = serviceDiscovery.getAppFromServiceDiscoveryOrder("not-a-service", null);
        assertFalse("No service should be found here!", service.isPresent());
    }

    @Test
    public void testNotAuthorizedComputeADFCall() throws Exception {
        Mockito.doReturn(response).when(serviceDiscovery).getAllDeployedAdfs(any(ServiceDiscoveryConfig.CloudOSVersion.class));
        Mockito.when(response.statusCode()).thenReturn(401).thenReturn(401).thenReturn(404).thenReturn(404);
        final boolean expectedResultReceived = expectFailGetComputeServices(serviceDiscovery);
        assertTrue("GetComputeServices should have failed", expectedResultReceived);
        verify(response, times(6)).statusCode();
    }

    @Test
    public void testAnomalousEmptySetFromGK() throws Exception {
        final ServiceDiscovery discovery = spy(new ServiceDiscovery());
        Mockito.doReturn(makeEmptyDefinitions()).when(discovery).parseGetAllDeployedAdfs(any(ServiceDiscoveryConfig.CloudOSVersion.class));
        validateThrows(ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.SERVICE_DISCOVERY_EXCEPTION),
                () -> discovery.getDeployedADFs(ServiceDiscoveryConfig.CloudOSVersion.COSV2));
    }

    @Test
    public void testAnomalousEmptySetFromNotificationSink() throws Exception {
        final ServiceDiscovery discovery = spy(new ServiceDiscovery());
        Mockito.doReturn(makeEmptyDefinitions()).when(discovery).parseGetAllDeployedAdfs(any(ServiceDiscoveryConfig.CloudOSVersion.class));
        final ApplicationDefinitions apps = discovery.getDeployedADFs(ServiceDiscoveryConfig.CloudOSVersion.COSV3);
        assertTrue("getDeployedADFs should return without exception for COSV3", apps.appDefinitions.isEmpty());
    }

    @Test
    @SneakyThrows
    public void testPortfolioVersionCoverage() {
        final ServiceDiscovery discovery = spy(new ServiceDiscovery());
        Mockito.doReturn(makeMockDefinitions()).when(discovery).parseGetAllDeployedAdfs(any(ServiceDiscoveryConfig.CloudOSVersion.class));
        final boolean expectedResultReceived = expectSucceedGetComputeServices(discovery);
        assertTrue("GetComputeServices should have succeeded", expectedResultReceived);
        final Multimap<String, AppDefinition> deployedServices = adfs.getAppDefinitions();
        // get the first adf we find, doesn't matter which.
        final Map.Entry<String, AppDefinition> entry = deployedServices.entries().iterator().next();
        final AppDefinition goodAdf = entry.getValue();
        final AppDefinition goodAdfWithNullPortfilioVersion = goodAdf.withPortfolioVersion(null);
        Mockito.doReturn(response).when(discovery).getOneAdf(anyString(), anyString(), any(ServiceDiscoveryConfig.CloudOSVersion.class));

        // Ensure we get an exception when we get an unexpected response code
        Mockito.when(response.statusCode()).thenReturn(401);
        Mockito.when(response.body()).thenReturn(new ObjectMapper().writeValueAsString(goodAdf));
        validateThrows(ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.NOT_AUTHORIZED),
                () -> discovery.getAppFromServiceDiscoveryOrder(entry.getKey(), goodAdf.getPortfolioVersion()).isEmpty());
        // Ensure we get what we expect (exact portfolio lookup)
        Mockito.when(response.statusCode()).thenReturn(200);
        Mockito.when(response.body()).thenReturn(new ObjectMapper().writeValueAsString(goodAdf));
        assertFalse(discovery.getAppFromServiceDiscoveryOrder(entry.getKey(), goodAdf.getPortfolioVersion()).isEmpty());
        // Ensure we get what we expect (DEPLOYED)
        Mockito.when(response.body()).thenReturn(new ObjectMapper().writeValueAsString(goodAdf));
        assertFalse(discovery.getAppFromServiceDiscoveryOrder(entry.getKey(), "DEPLOYED").isEmpty());
        // Ensure we get what we expect (null)
        Mockito.when(response.body()).thenReturn(new ObjectMapper().writeValueAsString(goodAdf));
        assertFalse(discovery.getAppFromServiceDiscoveryOrder(entry.getKey(), new String()).isEmpty());
        // Now mess with the response to simulate some deployment anomaly
        // We should get empty since the received porfolio (from GK) doesn't match what we asked for
        Mockito.when(response.body()).thenReturn(new ObjectMapper().writeValueAsString(goodAdfWithNullPortfilioVersion));
        assertTrue(discovery.getAppFromServiceDiscoveryOrder(entry.getKey(), goodAdf.getPortfolioVersion()).isEmpty());
    }

    @Test
    public void testGetAppFromServiceDiscoveryOrder() throws Exception {
        try (final MockedStatic<ServiceDiscoveryConfig> serviceDiscoveryConfig = Mockito.mockStatic(ServiceDiscoveryConfig.class)) {
            final ServiceDiscoveryConfig sdConfigMock = spy(sdConfig);
            final List<ServiceDiscoveryConfig.CloudOSVersion> discoveryOrdersMock = Arrays.asList(ServiceDiscoveryConfig.CloudOSVersion.COSV3, ServiceDiscoveryConfig.CloudOSVersion.COSV2);

            Mockito.doReturn(discoveryOrdersMock).when(sdConfigMock).getDiscoveryOrders();
            serviceDiscoveryConfig.when(() -> ServiceDiscoveryConfig.getInstance()).thenReturn(sdConfigMock);
            final ServiceDiscovery discovery = spy(new ServiceDiscovery());
            final AppDefinition v2App = AppDefinition.builder().appName("app").portfolioVersion("v2version").build();
            final AppDefinition v3App = AppDefinition.builder().appName("app").portfolioVersion("v3version").build();

            Mockito.doReturn(Optional.ofNullable(v2App)).when(discovery).getAppFromGatekeeper("app", "deployed", ServiceDiscoveryConfig.CloudOSVersion.COSV2);
            Mockito.doReturn(Optional.ofNullable(v3App)).when(discovery).getAppFromGatekeeper("app", "deployed", ServiceDiscoveryConfig.CloudOSVersion.COSV3);
            final Optional<AppDefinition> appInOrder = discovery.getAppFromServiceDiscoveryOrder("app", "deployed");
            assertTrue(appInOrder.isPresent());
            assertEquals(appInOrder.get().getPortfolioVersion(), "v3version");
        }

        try (final MockedStatic<ServiceDiscoveryConfig> serviceDiscoveryConfig = Mockito.mockStatic(ServiceDiscoveryConfig.class)) {
            final ServiceDiscoveryConfig sdConfigMock = spy(sdConfig);
            final List<ServiceDiscoveryConfig.CloudOSVersion> discoveryOrdersMock = Arrays.asList(ServiceDiscoveryConfig.CloudOSVersion.COSV2, ServiceDiscoveryConfig.CloudOSVersion.COSV3);

            Mockito.doReturn(discoveryOrdersMock).when(sdConfigMock).getDiscoveryOrders();
            serviceDiscoveryConfig.when(() -> ServiceDiscoveryConfig.getInstance()).thenReturn(sdConfigMock);
            final ServiceDiscovery discovery = spy(new ServiceDiscovery());
            final AppDefinition v2App = AppDefinition.builder().appName("app").portfolioVersion("v2version").build();
            final AppDefinition v3App = AppDefinition.builder().appName("app").portfolioVersion("v3version").build();

            Mockito.doReturn(Optional.ofNullable(v2App)).when(discovery).getAppFromGatekeeper("app", "deployed", ServiceDiscoveryConfig.CloudOSVersion.COSV2);
            Mockito.doReturn(Optional.ofNullable(v3App)).when(discovery).getAppFromGatekeeper("app", "deployed", ServiceDiscoveryConfig.CloudOSVersion.COSV3);
            final Optional<AppDefinition> appInOrder = discovery.getAppFromServiceDiscoveryOrder("app", "deployed");
            assertTrue(appInOrder.isPresent());
            assertEquals(appInOrder.get().getPortfolioVersion(), "v2version");
        }
    }

    @Test
    public void testGetDeployedComputeServices() throws Exception {

        try (final MockedStatic<ServiceDiscoveryConfig> serviceDiscoveryConfig = Mockito.mockStatic(ServiceDiscoveryConfig.class)) {
            final ServiceDiscoveryConfig sdConfigMock = spy(sdConfig);
            final List<ServiceDiscoveryConfig.CloudOSVersion> discoveryOrdersMock = Arrays.asList(ServiceDiscoveryConfig.CloudOSVersion.COSV3, ServiceDiscoveryConfig.CloudOSVersion.COSV2);

            Mockito.doReturn(discoveryOrdersMock).when(sdConfigMock).getDiscoveryOrders();
            serviceDiscoveryConfig.when(() -> ServiceDiscoveryConfig.getInstance()).thenReturn(sdConfigMock);
            final ServiceDiscovery discovery = spy(new ServiceDiscovery());
            final ApplicationDefinitions v2Apps = ApplicationDefinitions.builder().appDefinitions(
                    Arrays.asList(AppDefinition.builder().appName("app").portfolioVersion("1.0.11").batch(BatchJobDefinition.builder().computeSpecification(ComputeSpecification.builder().build()).build()).build())).build();
            final ApplicationDefinitions v3Apps = ApplicationDefinitions.builder().appDefinitions(
                    Arrays.asList(AppDefinition.builder().appName("app").portfolioVersion("release").batch(BatchJobDefinition.builder().computeSpecification(ComputeSpecification.builder().build()).build()).build())).build();

            Mockito.doReturn(v2Apps).when(discovery).getDeployedADFs(ServiceDiscoveryConfig.CloudOSVersion.COSV2);
            Mockito.doReturn(v3Apps).when(discovery).getDeployedADFs(ServiceDiscoveryConfig.CloudOSVersion.COSV3);
            final Services serviceInOrder = discovery.getDeployedComputeServices();
            final Map<String, Collection<AppDefinition>> appsInOrders = serviceInOrder.getAppDefinitions().asMap();
            assertEquals(appsInOrders.size(), 3);
            final String[] expectedServiceKeys = new String[]{"app", "app|release", "app|1.0.11"};
            assertArrayEquals(expectedServiceKeys, serviceInOrder.getAppDefinitions().asMap().keySet().toArray());
            assertEquals("release", appsInOrders.get("app").iterator().next().getPortfolioVersion());
        }

        try (final MockedStatic<ServiceDiscoveryConfig> serviceDiscoveryConfig = Mockito.mockStatic(ServiceDiscoveryConfig.class)) {
            final ServiceDiscoveryConfig sdConfigMock = spy(sdConfig);
            final List<ServiceDiscoveryConfig.CloudOSVersion> discoveryOrdersMock = Arrays.asList(ServiceDiscoveryConfig.CloudOSVersion.COSV2, ServiceDiscoveryConfig.CloudOSVersion.COSV3);

            Mockito.doReturn(discoveryOrdersMock).when(sdConfigMock).getDiscoveryOrders();
            serviceDiscoveryConfig.when(() -> ServiceDiscoveryConfig.getInstance()).thenReturn(sdConfigMock);
            final ServiceDiscovery discovery = spy(new ServiceDiscovery());
            final ApplicationDefinitions v2Apps = ApplicationDefinitions.builder().appDefinitions(
                    Arrays.asList(AppDefinition.builder().appName("app").portfolioVersion("1.0.11").batch(BatchJobDefinition.builder().computeSpecification(ComputeSpecification.builder().build()).build()).build())).build();
            final ApplicationDefinitions v3Apps = ApplicationDefinitions.builder().appDefinitions(
                    Arrays.asList(AppDefinition.builder().appName("app").portfolioVersion("release").batch(BatchJobDefinition.builder().computeSpecification(ComputeSpecification.builder().build()).build()).build())).build();

            Mockito.doReturn(v2Apps).when(discovery).getDeployedADFs(ServiceDiscoveryConfig.CloudOSVersion.COSV2);
            Mockito.doReturn(v3Apps).when(discovery).getDeployedADFs(ServiceDiscoveryConfig.CloudOSVersion.COSV3);
            final Services serviceInOrder = discovery.getDeployedComputeServices();
            final Map<String, Collection<AppDefinition>> appsInOrders = serviceInOrder.getAppDefinitions().asMap();
            assertEquals(appsInOrders.size(), 3);
            final String[] expectedServiceKeys = new String[]{"app", "app|release", "app|1.0.11"};
            assertArrayEquals(expectedServiceKeys, serviceInOrder.getAppDefinitions().asMap().keySet().toArray());
            assertEquals("1.0.11", appsInOrders.get("app").iterator().next().getPortfolioVersion());
        }
    }
}