package com.autodesk.compute.discovery;

import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.*;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.autodesk.compute.model.cosv2.ComponentDefinition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestStateMachineManager {
    private static final LocalAppDefinitionsLoader appDefinitions = new LocalAppDefinitionsLoader();

    private static String appMoniker;

    @Mock
    private AWSStepFunctions sfn;

    @Spy
    @InjectMocks
    private StateMachineManager stateMachineManager;

    @BeforeClass
    public static void beforeClass() throws ComputeException {
        appMoniker = System.getProperty("APP_MONIKER", "fpccomp-c-uw2-sb");
        appDefinitions.loadTestAppDefinitions();
    }

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
    }

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

    @Test
    public void deleteResourcesNoResources() {
        stateMachineManager.deleteResources(Optional.empty());
        verify(sfn, never()).deleteActivity(any(DeleteActivityRequest.class));
        verify(sfn, never()).deleteStateMachine(any(DeleteStateMachineRequest.class));
    }

    long countComputeSpecifications(final List<ComponentDefinition> components) {
        if (components == null || components.size() == 0)
            return 0;
        return components.stream().filter(item -> item.getComputeSpecification() != null).count();
    }

    @Test
    public void deleteServiceResources() {
        final Collection<AppDefinition> localAppDefinitions = appDefinitions.testAppDefinitionsMultimap.values();
        final AppDefinition targetAppDefinition = getTargetAppDefinition(localAppDefinitions, appMoniker);
        final ServiceResources resources = new ServiceResources(targetAppDefinition, ServiceResources.ServiceDeployment.DEPLOYED);
        final long components = countComputeSpecifications(targetAppDefinition.getComponents());
        final int batches = targetAppDefinition.getBatches().size();
        final int workers = (int) components + batches;

        Assert.assertTrue(workers > 0);
        stateMachineManager.deleteResources(Optional.of(resources));
        verify(sfn, times(workers)).deleteActivity(any(DeleteActivityRequest.class));
        verify(sfn, times(workers)).deleteStateMachine(any(DeleteStateMachineRequest.class));
    }

    @Test
    public void createStateMachineCreateRequired() {
        doThrow(new StateMachineDoesNotExistException("from-test")).when(sfn).describeStateMachine(any(DescribeStateMachineRequest.class));
        final Collection<AppDefinition> localAppDefinitions = appDefinitions.testAppDefinitionsMultimap.values();
        final AppDefinition targetAppDefinition = getTargetAppDefinition(localAppDefinitions, appMoniker);
        final ServiceResources resources = new ServiceResources(targetAppDefinition, ServiceResources.ServiceDeployment.DEPLOYED);

        stateMachineManager.createStateMachine(resources.getWorkers().values().iterator().next(), "arn:activity", "arn:role");
        verify(sfn, times(1)).describeStateMachine(any(DescribeStateMachineRequest.class));
        verify(sfn, times(1)).createStateMachine(any(CreateStateMachineRequest.class));
        verify(sfn, never()).updateStateMachine(any(UpdateStateMachineRequest.class));
    }

    @Test
    public void createStateMachineUpdateRequired() {
        final Collection<AppDefinition> localAppDefinitions = appDefinitions.testAppDefinitionsMultimap.values();
        final AppDefinition targetAppDefinition = getTargetAppDefinition(localAppDefinitions, appMoniker);
        final ServiceResources resources = new ServiceResources(targetAppDefinition, ServiceResources.ServiceDeployment.DEPLOYED);

        stateMachineManager.createStateMachine(resources.getWorkers().values().iterator().next(), "arn:activity", "arn:role");

        verify(sfn, times(1)).describeStateMachine(any(DescribeStateMachineRequest.class));
        verify(sfn, never()).createStateMachine(any(CreateStateMachineRequest.class));
        verify(sfn, times(1)).updateStateMachine(any(UpdateStateMachineRequest.class));
    }

}
