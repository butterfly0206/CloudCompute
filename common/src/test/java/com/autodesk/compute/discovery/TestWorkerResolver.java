package com.autodesk.compute.discovery;

import com.autodesk.compute.common.*;
import com.autodesk.compute.configuration.ComputeConstants;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.Services;
import com.autodesk.compute.model.cosv2.ApiOperationType;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.autodesk.compute.model.cosv2.AppState;
import com.autodesk.compute.model.cosv2.ApplicationDefinitions;
import com.autodesk.compute.test.categories.UnitTests;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@Category(UnitTests.class)
@Slf4j
public class TestWorkerResolver {

    private static final LocalAppDefinitionsLoader appDefinitions = new LocalAppDefinitionsLoader();

    private static String appMoniker;

    private ServiceDiscovery serviceDiscovery;

    private WorkerResolver workerResolver;

    @Mock
    private ResourceManager resourceManager;

    @Mock
    private RedisClient client;

    private RedisCacheLoader redisCacheLoader;

    private FakeRedisCommands redisCommands;

    private RedisCache<AppDefinition> appDefinitionCache;

    private RedisCache<AppState> appStateCache;

    private RedisCache<String> timeCache;

    @Mock
    private StatefulRedisConnection<String, String> connection;

    @Spy
    private ServiceCacheLoader serviceCacheLoader;

    private AppDefinition getTargetAppDefinition(final Collection<AppDefinition> appDefinitions, final String appName) {
        AppDefinition appDefinition = null;
        try {
            appDefinition = appDefinitions.stream()
                    .filter(adf -> adf.getAppName().equals(appName))
                    .findAny()
                    .orElse(null);
        } catch (final Throwable e) {
            fail("Unexpected exception getting services: " + e);
        }
        return appDefinition;
    }

    @BeforeClass
    public static void beforeClass() throws ComputeException {
        appMoniker = System.getProperty("APP_MONIKER", "fpccomp-c-uw2-sb");
        appDefinitions.loadTestAppDefinitions();
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.openMocks(this);
        // Set up fake redis cache
        this.redisCommands = new FakeRedisCommands();
        this.appDefinitionCache = spy(new RedisCache<>(AppDefinition.class));
        this.appStateCache = spy(new RedisCache<>(AppState.class));
        this.timeCache = spy(new RedisCache<>(String.class));
        doReturn(client).when(appDefinitionCache).makeRedisClient(any(RedisURI.class));
        doReturn(client).when(appStateCache).makeRedisClient(any(RedisURI.class));
        doReturn(client).when(timeCache).makeRedisClient(any(RedisURI.class));
        doReturn(connection).when(client).connect();
        doReturn(redisCommands).when(connection).sync();
        this.redisCacheLoader = new RedisCacheLoader(appDefinitionCache, appStateCache, timeCache);

        // Set up worker resolver and its underlying cache management system
        this.serviceDiscovery = mock(ServiceDiscovery.class);
        this.workerResolver = spy(new WorkerResolver(this.serviceDiscovery));
        doReturn(serviceCacheLoader).when(workerResolver).makeServiceCacheLoader(any(ServiceDiscovery.class));
        doReturn(redisCacheLoader).when(workerResolver).makeRedisCacheLoader();
        doReturn(appDefinitionCache).when(workerResolver).makeRedisCache(any(Class.class));
        doReturn(resourceManager).when(workerResolver).getResourceManager();
        doReturn(redisCacheLoader).when(serviceCacheLoader).makeRedisCacheLoader();
        try {
            Mockito.doNothing().when(workerResolver).forceSyncResources();
            workerResolver.initialize();
        } catch (final ComputeException e) {
            fail(makeString("Unexpected exception setting up mocks: ", e.getMessage(), e.getStackTrace().toString()));
        }
        assertNotNull(workerResolver);
        assertTrue(workerResolver.isInitialized());

        // Read local disk ADFs for worker resolution tests
        try {
            // Each application only gets a single deployed version returned. Any other version is available only when
            // asked for directly.
            // So in this case, we'll declare version 4.5.6 to be the deployed version of comptest-c-uw2-sb, and 4.5.7
            // to be the test version.
            final Map<String, AppDefinition> deployedAdfs = appDefinitions.testAppDefinitionsMultimap.asMap().entrySet().stream()
                    .filter(entry -> ComputeStringOps.isNullOrEmpty(ServiceWithVersion.fromKey(entry.getKey()).getPortfolioVersion()))
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                        // Last thing in the list will be the one that is deployed. Newer stuff is in test.
                        final Collection<AppDefinition> definitions = entry.getValue();
                        return definitions.stream().skip(definitions.size() - 1).findFirst().get();
                    }));
            final ApplicationDefinitions definitions = ApplicationDefinitions.builder()
                    .appDefinitions(deployedAdfs.values()).errors(Collections.emptyList()).build();

            // Mock service discovery: put 4.5.6 in the test results, and 4.5.7 in the deployed.
            when(serviceDiscovery.getDeployedADFs(any(ServiceDiscoveryConfig.CloudOSVersion.class))).thenReturn(definitions);
            final Optional<AppDefinition> appDef456 = Optional.of(appDefinitions.testAppDefinitions.get("src/test/resources/adf-collection/multi-version/comptest-456.json"));
            final Optional<AppDefinition> appDef457 = Optional.of(appDefinitions.testAppDefinitions.get("src/test/resources/adf-collection/multi-version/comptest-457.json"));
            final Optional<AppDefinition> fpccompApp = Optional.of(appDefinitions.testAppDefinitions.get("src/test/resources/adf-collection/self/fpccomp-c-uw2-sb.json"));
            final Optional<AppDefinition> noWorkers = Optional.of(appDefinitions.testAppDefinitions.get("src/test/resources/adf-collection/step-function/activity-arn.json"));
            when(serviceDiscovery.getAppFromServiceDiscoveryOrder(eq("comptest-c-uw2-sb"), eq("deployed"))).thenReturn(appDef456);
            when(serviceDiscovery.getAppFromServiceDiscoveryOrder(eq("comptest-c-uw2-sb"), eq("4.5.6"))).thenReturn(appDef456);
            when(serviceDiscovery.getAppFromServiceDiscoveryOrder(eq("comptest-c-uw2-sb"), eq("4.5.7"))).thenReturn(appDef457);
            when(serviceDiscovery.getAppFromServiceDiscoveryOrder(eq("comptest-c-uw2-sb"), eq("4.5.8"))).thenReturn(Optional.empty());
            when(serviceDiscovery.getAppFromServiceDiscoveryOrder(eq("fpccomp-c-uw2-sb"), eq("deployed"))).thenReturn(fpccompApp);
            when(serviceDiscovery.getAppFromServiceDiscoveryOrder(eq("fpccomp-c-uw2-sb"), isNull())).thenReturn(fpccompApp);
            when(serviceDiscovery.getAppFromServiceDiscoveryOrder(eq("acmel-c-uw2-sb"), isNull())).thenReturn(noWorkers);
            when(serviceDiscovery.getAppFromServiceDiscoveryOrder(eq("acmel-c-uw2-sb"), eq("deployed"))).thenReturn(noWorkers);
            final Services services = new Services(ServiceDiscovery.getServicesFromAppDefinitions(definitions.appDefinitions));
            when(serviceDiscovery.getDeployedComputeServices()).thenReturn(services);
            Mockito.doNothing().when(workerResolver).forceSyncResources();
            workerResolver.refreshIfEmpty();
        } catch (final Exception e) {
            fail(makeString("Unexpected exception setting up mocks: ", e.getMessage(), e.getStackTrace().toString()));
        }
    }

    @After
    public void afterEach() {
        redisCacheLoader.close();
    }

    @Test
    public void testInstantiation() {
        assertNotNull(workerResolver);
        assertFalse(appMoniker.isEmpty());
    }

    @Test
    public void testGetTestServices() {
        appDefinitions.testAppDefinitions.values().forEach(adf -> log.info("ResourceManager has service " + adf.getAppName()));
        assertFalse("App definitions list is empty", appDefinitions.testAppDefinitions.isEmpty());
        assertNotEquals("No app definitions for resource manager", 0, appDefinitions.testAppDefinitions.size());
    }

    @Test
    public void testValidateTestServices() {
        int foundDefinitions = 0;
        for (final String filename : appDefinitions.testAppDefinitions.keySet()) {
            // Paths were normalized
            final String adfSource = filename.substring(filename.lastIndexOf('/') + 1);
            final AppDefinition appDefinition = appDefinitions.testAppDefinitions.get(filename);

            assertNotNull(appDefinition);

            switch (adfSource) {
                case "everything.json":
                case "apigateway-container.json":
                    foundDefinitions++;
                    assertEquals("components != 2 with " + adfSource, 2, appDefinition.getComponents().size());
                    assertNotNull("null portfolio version with " + adfSource, appDefinition.getPortfolioVersion());
                    break;
                case "apigateway-custom-auth.json":
                case "apigateway.json":
                case "activity-arn.json":
                case "activity-ref.json":
                case "lambda-arn.json":
                case "lambda-ref.json":
                    foundDefinitions++;
                    assertEquals("components != 1 with " + adfSource, 1, appDefinition.getComponents().size());
                    assertNotNull("null portfolio version with " + adfSource, appDefinition.getPortfolioVersion());
                    break;
                case "apigateway-onboarding.json":
                case "lambda-dlq-vpc.json":
                case "lambda-dlq.json":
                case "lambda-schedule-trigger.json":
                case "lambda-sns-trigger.json":
                case "lambda-vpc.json":
                case "lambda-without-trigger.json":
                    foundDefinitions++;
                    assertTrue(appDefinition.getComponents() == null || appDefinition.getComponents().isEmpty());
                    break;
                case "comptest-456.json":
                case "comptest-457.json":
                case "fpccomp-c-uw2-sb.json":
                    foundDefinitions++;
                    assertTrue("No compute specification found in " + adfSource, appDefinition.getComponents().stream()
                            .anyMatch(item -> item.getComputeSpecification() != null));
                    break;
                default:
                    log.error("Unknown adf file resource:" + adfSource);
                    break;
            }
        }
        assertEquals("Wrong number of test definitions found", 18, foundDefinitions);
    }

    @Test
    public void testGetPortfolioVersionsOfTheSameWorker() throws ComputeException {
        final Optional<ServiceResources.WorkerResources> firstWorker = workerResolver.lookupWorker("comptest-c-uw2-sb", "sample1", "4.5.6");
        final Optional<ServiceResources.WorkerResources> secondWorker = workerResolver.lookupWorker("comptest-c-uw2-sb", "sample1", "4.5.7");
        final Optional<ServiceResources.WorkerResources> notPresentWorker = workerResolver.lookupWorker("comptest-c-uw2-sb", "sample1", "4.5.8");
        assertTrue("No 4.5.6 worker was found", firstWorker.isPresent());
        assertTrue("No 4.5.7 worker was found", secondWorker.isPresent());
        assertTrue("4.5.8 worker really shouldn't be there", notPresentWorker.isEmpty());
    }

    @Test
    public void testGetWorkerResources() throws ComputeException {
        final ServiceResources.WorkerResources testResources = Mockito.mock(ServiceResources.WorkerResources.class, Mockito.CALLS_REAL_METHODS);
        Mockito.doReturn("mockId").when(testResources).getWorkerIdentifier();
        Mockito.doReturn(Optional.of(testResources)).when(workerResolver).lookupWorker(anyString(), eq("mockWorker"), anyString());

        Optional<ServiceResources.WorkerResources> resources = Optional.empty();
        resources = workerResolver.getWorkerResources(appMoniker, "mockWorker", "1.0.15");
        assertTrue("No resources for " + appMoniker, resources.isPresent());

        // ensure we're getting our mock resources back (look for "mockId")
        final String id = resources.get().getWorkerIdentifier();
        assertEquals("Bad id for " + appMoniker, "mockId", id);
    }

    @Test
    public void testGettingAdfWithNoWorkers() throws ComputeException {
        final Optional<ServiceResources.WorkerResources> resources = workerResolver.getWorkerResources("acmel-c-uw2-sb", "worker", null);
        assertEquals("No worker should be returned", Optional.empty(), resources);
    }

    @Test
    public void testLookupBogusWorker() throws ComputeException {
        Optional<ServiceResources.WorkerResources> resources = Optional.empty();
        Mockito.doReturn(Optional.empty()).when(workerResolver).getWorkerResources(eq(appMoniker), eq("bogusWorker"), isNull());
        resources = workerResolver.getWorkerResources(appMoniker, "bogusWorker", null);
        assertNull("Found resources for bogus worker!", resources.orElse(null));
    }

    @Test
    public void testHasService() throws ComputeException {
        Assert.assertTrue(workerResolver.hasService(appMoniker));
        Assert.assertFalse(workerResolver.hasService("not-a-moniker"));
    }

    @Test
    public void testAddServiceResources() {
        final Collection<AppDefinition> localAppDefinitions = appDefinitions.testAppDefinitionsMultimap.values();
        final AppDefinition appDef = getTargetAppDefinition(localAppDefinitions, appMoniker);
        workerResolver.addServiceResources(appDef, ApiOperationType.CREATE_OR_UPDATE_APPLICATION);
        verify(resourceManager, times(1)).syncResource(
                any(AppDefinition.class), nullable(Optional.class), any(ApiOperationType.class));
    }

    @Test
    public void testForceSyncResources() throws ComputeException {
        doCallRealMethod().when(workerResolver).forceSyncResources();
        workerResolver.forceSyncResources();
        verify(serviceDiscovery, times(1)).getDeployedComputeServices();
        verify(resourceManager, times(1)).forceSyncResources(any(Services.class));
    }

    @Test
    public void testInvalidate() throws ComputeException {
        doCallRealMethod().when(workerResolver).forceSyncResources();
        workerResolver.forceSyncResources();
        verify(serviceDiscovery, times(1)).getDeployedComputeServices();
        verify(resourceManager, times(1)).forceSyncResources(any(Services.class));
        final Pair<RedisCache.Present, Optional<AppDefinition>> inCache = appDefinitionCache.getIfPresent(appMoniker, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.PRESENT, inCache.getLeft());
        Assert.assertTrue(inCache.getRight().isPresent());
        workerResolver.invalidateResource(appMoniker);
        final Pair<RedisCache.Present, Optional<AppDefinition>> notInCache = appDefinitionCache.getIfPresent(appMoniker, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.NOT_PRESENT, notInCache.getLeft());
        Assert.assertTrue(notInCache.getRight().isEmpty());
    }

    @Test
    public void testGetPortfolioVersionsOfTheSameWorkerWithEnabledRedisCache() throws ComputeException {
        doCallRealMethod().when(workerResolver).forceSyncResources();
        workerResolver.forceSyncResources();
        final Optional<ServiceResources.WorkerResources> firstWorker = workerResolver.lookupWorker("comptest-c-uw2-sb", "sample1", "4.5.6");
        final Optional<ServiceResources.WorkerResources> secondWorker = workerResolver.lookupWorker("comptest-c-uw2-sb", "sample1", "4.5.7");
        final Optional<ServiceResources.WorkerResources> notPresentWorker = workerResolver.lookupWorker("comptest-c-uw2-sb", "sample1", "4.5.8");
        assertTrue("No 4.5.6 worker was found", firstWorker.isPresent());
        assertTrue("No 4.5.7 worker was found", secondWorker.isPresent());
        assertTrue("4.5.8 worker really shouldn't be there", notPresentWorker.isEmpty());

        final Pair<RedisCache.Present, Optional<AppDefinition>> inCache = appDefinitionCache.getIfPresent(appMoniker, ComputeUtils.TimeoutSettings.FAST);
        Assert.assertEquals(RedisCache.Present.PRESENT, inCache.getLeft());
        workerResolver.invalidateResource(appMoniker);
        redisCacheLoader.addDeployedApplication(inCache.getRight().get());
        final Optional<ServiceResources.WorkerResources> computeWorker = workerResolver.lookupWorker(appMoniker, "sample1", null);
        Assert.assertTrue(computeWorker.isPresent());
        verify(client, times(3)).connect();
        final MockMetrics metrics = (MockMetrics) Metrics.getInstance();
        Assert.assertTrue(metrics.getAllCapturedMetrics().get(ComputeConstants.MetricsNames.REDIS_CACHE_HIT).size() > 1);
    }

    @Test
    public void testAppStateCache() throws ComputeException {
        // The intent is to incur caching, which we can verify via coverage testing
        appStateCache.clear(ComputeUtils.TimeoutSettings.SLOW);
        final AppState compTestAppState_first = redisCacheLoader.getAppState("comptest-c-uw2-sb");
        final AppState compTestAppState_second = redisCacheLoader.getAppState("comptest-c-uw2-sb");
        assertEquals(compTestAppState_first, compTestAppState_second);
    }

    @Test
    public void testTimeCache() throws ComputeException {
        // The intent is to incur caching, which we can verify via coverage testing
        timeCache.clear(ComputeUtils.TimeoutSettings.SLOW);
        final Instant forceSyncTime_first = redisCacheLoader.getLastForceSyncTime();
        final Instant now = Instant.now();
        redisCacheLoader.setLastForceSyncTime(now);
        final Instant forceSyncTime_second = redisCacheLoader.getLastForceSyncTime();
        assertEquals(Instant.EPOCH, forceSyncTime_first);
        assertEquals(now.toEpochMilli(), forceSyncTime_second.toEpochMilli());
    }

    @Test
    public void testCollidingAdfAndAppStateKeys() throws ComputeException {
        final int AN_HOUR = 3600;
        final Collection<AppDefinition> localAppDefinitions = appDefinitions.testAppDefinitionsMultimap.values();
        final AppDefinition appDef = getTargetAppDefinition(localAppDefinitions, appMoniker);
        final AppState appState = AppState.builder(appMoniker).build();
        // Clear the caches, and populate them with different objects using the same key.
        // The underlying RedisCache should add type information and prevent collisions
        this.appDefinitionCache.clear(ComputeUtils.TimeoutSettings.SLOW);
        this.appStateCache.clear(ComputeUtils.TimeoutSettings.SLOW);
        this.appDefinitionCache.set(appMoniker, appDef, AN_HOUR, ComputeUtils.TimeoutSettings.SLOW);
        this.appStateCache.set(appMoniker, appState, AN_HOUR, ComputeUtils.TimeoutSettings.SLOW);
        final Pair<RedisCache.Present, Optional<AppDefinition>> fromCache = this.appDefinitionCache.getIfPresent(appMoniker, ComputeUtils.TimeoutSettings.FAST);
        assertEquals(RedisCache.Present.PRESENT, fromCache.getLeft());
        assertFalse(fromCache.getRight().isEmpty());
        assertEquals(appDef, fromCache.getRight().get());
    }
}
