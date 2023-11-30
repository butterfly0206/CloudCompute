package com.autodesk.compute.discovery;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.ListActivitiesRequest;
import com.amazonaws.services.stepfunctions.model.ListActivitiesResult;
import com.amazonaws.services.stepfunctions.model.ListStateMachinesRequest;
import com.amazonaws.services.stepfunctions.model.ListStateMachinesResult;
import com.autodesk.compute.common.ComputeAWSStepFunctionsDriver;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.Services;
import com.autodesk.compute.model.cosv2.ApiOperationType;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.autodesk.compute.model.cosv2.ComponentDefinition;
import com.autodesk.compute.test.TestHelper;
import com.autodesk.compute.test.categories.UnitTests;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


@Category(UnitTests.class)
@Slf4j
public class TestResourceManager {

    private static final LocalAppDefinitionsLoader appDefinitions = new LocalAppDefinitionsLoader();

    private static String appMoniker;

    private ResourceManager resourceManager;

    private ServiceDiscovery serviceDiscovery;

    @Mock
    private StateMachineManager stateMachineManager;

    @Mock
    private WorkerResolver workerResolver;

    @Mock
    private ServiceResources serviceResources;

    @Mock
    private ServiceResources.BatchWorkerResources batchWorker;

    @Mock
    private ServiceResources.ComponentWorkerResources pollingWorker;

    @Mock
    private AWSStepFunctions sfn;

    @Spy
    @InjectMocks
    private ComputeAWSStepFunctionsDriver stepFunctionsDriver;

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
        this.serviceDiscovery = mock(ServiceDiscovery.class);
        this.resourceManager = spy(new ResourceManager());
        try {
            Mockito.doNothing().when(resourceManager).forceSyncResources(any(Services.class));
            doReturn(stateMachineManager).when(resourceManager).makeStateMachineManager();
            doReturn(stepFunctionsDriver).when(resourceManager).makeComputeAWSStepFunctionsDriver();
            doReturn(
                    new Services(ServiceDiscovery.getServicesFromAppDefinitions(
                            appDefinitions.testAppDefinitionsMultimap.values())))
                    .when(serviceDiscovery).getDeployedComputeServices();
            resourceManager.initialize();
        } catch (final ComputeException e) {
            fail(makeString("Unexpected exception setting up mocks: ", e.getMessage(), e.getStackTrace().toString()));
        }
        assertNotNull(resourceManager);
        assertTrue(resourceManager.isInitialized());
    }

    @Test
    public void testInstantiation() {
        assertNotNull(resourceManager);
        assertFalse(appMoniker.isEmpty());
    }

    @Test
    public void testGetTestServices() {
        appDefinitions.testAppDefinitions.values().forEach( adf -> log.info("ResourceManager has service " + adf.getAppName()));
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
                    assertEquals("components != 1 with " + adfSource,1, appDefinition.getComponents().size());
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
    public void testSyncResourceNoOp() {
        final Collection<AppDefinition> localAppDefinitions = appDefinitions.testAppDefinitionsMultimap.values();
        final AppDefinition targetAppDefinition = getTargetAppDefinition(localAppDefinitions, appMoniker);
        final ServiceResources resources = resourceManager.syncResource(
                targetAppDefinition, Optional.empty(), ApiOperationType.READ_APPLICATION);
        // READ_APPLICATION does nothing, so we expect no modified resources back
        Assert.assertNull(resources);
        verify(stateMachineManager, never()).deleteStateMachine(anyString());
        verify(stateMachineManager, never())
                .createStateMachine(any(ServiceResources.WorkerResources.class), anyString(), anyString());
        verify(stateMachineManager, never()).deleteActivity(anyString());
        verify(stateMachineManager, never()).createActivity(anyString());
    }

    @Test
    public void testSyncResourcesDelete() {
        final Collection<AppDefinition> localAppDefinitions = appDefinitions.testAppDefinitionsMultimap.values();
        final AppDefinition targetAppDefinition = getTargetAppDefinition(localAppDefinitions, appMoniker);
        final ServiceResources existingResources = new ServiceResources(targetAppDefinition, ServiceResources.ServiceDeployment.DEPLOYED);
        final ServiceResources resources = resourceManager.syncResource(
                targetAppDefinition, Optional.of(existingResources), ApiOperationType.DELETE_APPLICATION);
        // DELETE_APPLICATION will try to delete all worker resources, returning null
        Assert.assertNull(resources);
        final long componentWorkers = targetAppDefinition.getComponents().stream()
                .filter(component -> component.getComputeSpecification() != null).count();
        final long batchWorkers = targetAppDefinition.getBatches().size();
        final int totalWorkers = (int) (componentWorkers + batchWorkers);
        verify(stateMachineManager, times(totalWorkers)).deleteStateMachine(anyString());
        verify(stateMachineManager, never())
                .createStateMachine(any(ServiceResources.WorkerResources.class), anyString(), anyString());
        verify(stateMachineManager, times(totalWorkers)).deleteActivity(anyString());
        verify(stateMachineManager, never()).createActivity(anyString());
    }

    @Test
    public void testDeleteResourcesNoResources() {
        doReturn(stateMachineManager).when(resourceManager).makeStateMachineManager();
        resourceManager.deleteResources(Optional.empty());
        verify(stateMachineManager, never()).deleteActivity(anyString());
        verify(stateMachineManager, never()).deleteStateMachine(anyString());
    }

    Answer<String> makeActivityArns(final int count) {
        final List<String> results = IntStream.range(0, count).mapToObj(
                num -> "arn:activity:" + num
        ).collect(Collectors.toList());
        return TestHelper.makeAnswer(results.toArray(new String[0]));
    }

    @Test
    public void testSyncResourcesCreateOrUpdateNoExisting() {
        final Collection<AppDefinition> localAppDefinitions = appDefinitions.testAppDefinitionsMultimap.values();
        final AppDefinition targetAppDefinition = getTargetAppDefinition(localAppDefinitions, appMoniker);
        final long componentWorkers = targetAppDefinition.getComponents().stream()
                .filter(component -> component.getComputeSpecification() != null).count();
        final long batchWorkers = targetAppDefinition.getBatches().size();
        final int totalWorkers = (int) (componentWorkers + batchWorkers);

        doAnswer(makeActivityArns(totalWorkers)).when(stateMachineManager).createActivity(anyString());

        final ServiceResources resources = resourceManager.syncResource(
                targetAppDefinition, Optional.empty(), ApiOperationType.CREATE_OR_UPDATE_APPLICATION);
        // CREATE_OR_UPDATE will make new resources returning non-null
        Assert.assertNotNull(resources);
        verify(stateMachineManager, never()).deleteStateMachine(anyString());
        verify(stateMachineManager, times(totalWorkers))
                .createStateMachine(any(ServiceResources.WorkerResources.class), anyString(), anyString());
        verify(stateMachineManager, never()).deleteActivity(anyString());
        verify(stateMachineManager, times(totalWorkers)).createActivity(anyString());
    }

    @Test
    public void testSyncResourcesCreateOrUpdateAlreadyExisting() {
        final Collection<AppDefinition> localAppDefinitions = appDefinitions.testAppDefinitionsMultimap.values();
        final AppDefinition targetAppDefinition = getTargetAppDefinition(localAppDefinitions, appMoniker);
        final long componentWorkers = targetAppDefinition.getComponents().stream()
                .filter(component -> component.getComputeSpecification() != null).count();
        final long batchWorkers = targetAppDefinition.getBatches().size();
        final int totalWorkers = (int) (componentWorkers + batchWorkers);
        final ServiceResources existingResources = new ServiceResources(targetAppDefinition, ServiceResources.ServiceDeployment.DEPLOYED);

        doAnswer(makeActivityArns(totalWorkers)).when(stateMachineManager).createActivity(anyString());

        final ServiceResources resources = resourceManager.syncResource(
                targetAppDefinition, Optional.of(existingResources), ApiOperationType.CREATE_OR_UPDATE_APPLICATION);
        // CREATE_OR_UPDATE will make new resources returning non-null
        Assert.assertNotNull(resources);
        verify(stateMachineManager, never()).deleteStateMachine(anyString());
        verify(stateMachineManager, times(totalWorkers))
                .createStateMachine(any(ServiceResources.WorkerResources.class), anyString(), anyString());
        verify(stateMachineManager, never()).deleteActivity(anyString());
        verify(stateMachineManager, never()).createActivity(anyString());
    }

    @Test
    public void testDeleteResourcesMultipleResources() {
        final Map<String, ServiceResources.WorkerResources> workerResources = Map.of(
                "batch-worker", batchWorker,
                "polling-worker", pollingWorker);
        doReturn("batch-id").when(batchWorker).getWorkerIdentifier();
        doReturn("arn:batch-activity").when(batchWorker).getActivityArn();
        doReturn("arn:batch-statemachine").when(batchWorker).getStateMachineArn();
        doReturn("arn:polling-activity").when(pollingWorker).getActivityArn();
        doReturn("arn:polling-statemachine").when(pollingWorker).getStateMachineArn();
        doReturn("polling-id").when(pollingWorker).getWorkerIdentifier();
        doReturn(workerResources).when(serviceResources).getWorkers();
        doReturn(stateMachineManager).when(resourceManager).makeStateMachineManager();
        resourceManager.deleteResources(Optional.of(serviceResources));
        verify(stateMachineManager, times(2)).deleteActivity(anyString());
        verify(stateMachineManager, times(2)).deleteStateMachine(anyString());
    }

    long countComputeSpecifications(final List<ComponentDefinition> components) {
        if (components == null || components.size() == 0)
            return 0;
        return components.stream().filter(item -> item.getComputeSpecification() != null).count();
    }

    Answer<String> createActivityAnswer() {
        return new Answer<>() {
            int currentCount = 0;

            @Override
            public String answer(final InvocationOnMock invocation) throws Throwable {
                return "arn:activity:" + currentCount++;
            }
        };
    }

    Answer<String> createStateMachineAnswer() {
        return new Answer<>() {
            int currentCount = 0;

            @Override
            public String answer(final InvocationOnMock invocation) throws Throwable {
                return "arn:statemachine:" + currentCount++;
            }
        };
    }

    @Test
    public void testForceSyncResourcesCreateAll() {
        // Override initialize() behavior
        Mockito.doCallRealMethod().when(resourceManager).forceSyncResources(any(Services.class));
        final Answer<ListActivitiesResult> activityAnswers = TestHelper.makeAnswer(
                new ListActivitiesResult().withActivities(Collections.emptyList()));
        doAnswer(activityAnswers).when(sfn).listActivities(any(ListActivitiesRequest.class));
        final Answer<ListStateMachinesResult> stateMachineAnswers = TestHelper.makeAnswer(
                new ListStateMachinesResult().withStateMachines(Collections.emptyList()));
        doAnswer(stateMachineAnswers).when(sfn).listStateMachines(any(ListStateMachinesRequest.class));
        doAnswer(createActivityAnswer()).when(stateMachineManager).createActivity(anyString());
        doAnswer(createStateMachineAnswer()).when(stateMachineManager).createStateMachine(
                any(ServiceResources.WorkerResources.class), anyString(), anyString());

        final Services foundServices = new Services(ServiceDiscovery.getServicesFromAppDefinitions(
                appDefinitions.testAppDefinitionsMultimap.values()));

        // Collect the deployed services only, and create the resources for those.
        final Map<String, AppDefinition> discoveredServices = foundServices.getAppDefinitions().asMap()
                .entrySet().stream().filter(item -> !item.getKey().contains("|"))
                .collect(toMap(Map.Entry::getKey, entry -> entry.getValue().iterator().next()));

        final long componentWorkers = discoveredServices.entrySet().stream()
                .mapToLong(entry -> countComputeSpecifications(entry.getValue().getComponents()))
                .sum();
        final long batchWorkers = discoveredServices.entrySet().stream()
                .mapToInt(entry -> entry.getValue().getBatches() != null ? entry.getValue().getBatches().size() : 0)
                .sum();

        final int totalWorkers = (int) (componentWorkers + batchWorkers);

        resourceManager.forceSyncResources(foundServices);
        verify(stateMachineManager, never()).deleteStateMachine(anyString());
        verify(stateMachineManager, times(totalWorkers))
                .createStateMachine(any(ServiceResources.WorkerResources.class), anyString(), anyString());
        verify(stateMachineManager, never()).deleteActivity(anyString());
        verify(stateMachineManager, times(totalWorkers)).createActivity(anyString());
    }

}
