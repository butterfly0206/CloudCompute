package com.autodesk.compute.common;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.message.SnsNotification;
import com.amazonaws.services.sns.message.SnsSubscriptionConfirmation;
import com.amazonaws.services.sns.message.SnsUnknownMessage;
import com.amazonaws.services.sns.message.SnsUnsubscribeConfirmation;
import com.autodesk.compute.api.impl.AdfUpdateMessageHandler;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.discovery.RedisCacheLoader;
import com.autodesk.compute.discovery.WorkerResolver;
import com.autodesk.compute.dynamodb.DynamoDBSnsClient;
import com.autodesk.compute.model.AppUpdate;
import com.autodesk.compute.model.SNSMessage;
import com.autodesk.compute.model.cosv2.*;
import com.autodesk.compute.model.dynamodb.AppUpdateMapper;
import lombok.SneakyThrows;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.mockito.Mockito.*;


// Tactics here were found in https://www.baeldung.com/mockito-final
public class TestAdfUpdateMessageHandler {

    private enum AppDefOptions {
        INCLUDE_BATCH,
        INCLUDE_COMPONENT,
        INCLUDE_BOTH
    }

    private SnsNotification notification;

    private SnsSubscriptionConfirmation subscriptionConfirmation;

    private SnsUnsubscribeConfirmation unsubscribeConfirmation;

    private SnsUnknownMessage unknownMessage;

    private HttpClient httpClient;  // For Sns messages

    @Mock
    private WorkerResolver workerResolver;

    @Mock
    private RedisCacheLoader cacheLoader;

    @Mock
    private AmazonSNS sns;

    @Mock
    private DynamoDBSnsClient dynamoDBSnsClient;

    @Spy
    @InjectMocks
    private AdfUpdateMessageHandler handler;

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
        httpClient = mock(HttpClientBuilder.create().build().getClass());
        notification = mock(makeSnsNotification().getClass());
        unknownMessage = mock(makeUnknownMessage().getClass());
        unsubscribeConfirmation = mock(makeUnsubscribeConfirmation().getClass());
        subscriptionConfirmation = mock(makeSubscriptionConfirmation().getClass());
        doReturn(workerResolver).when(handler).getWorkerResolver();
        doReturn(cacheLoader).when(handler).makeRedisCacheLoader();
        doReturn(sns).when(handler).makeAmazonSNS();
    }

    // The next four methods provide code for calling the private constructors of
    // the various message types handled by AdfUpdateMessageHandler.
    // All of the constructors follow the same pattern:
    // 1. There's a private static Builder class.
    // 2. Sometimes the Builder class requires an Apache HTTPClient in its constructor; sometimes not.
    // 3. Once constructed, some of the Builders require a property be set that looks like a URI.
    // 4. Then we call the constructor of the outer class that takes the Builder as its argument.
    @SneakyThrows
    private SnsNotification makeSnsNotification() {
        final Class<SnsNotification> clazz = SnsNotification.class;
        final Constructor<?> objConstructor = Arrays.stream(clazz.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1).findFirst().get();             // This is the constructor that takes a builder.
        objConstructor.setAccessible(true);                                             // Make the constructor callable
        final Class<?> builderClass = Class.forName(clazz.getCanonicalName() + "$Builder");   // Get the Builder Class object
        final Constructor<?> builderConstructor = Arrays.stream(builderClass.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1).findFirst().get();       // Get the constructor for the Builder that takes an HttpClient
        builderConstructor.setAccessible(true);                                         // Make the constructor callable
        final Object builder = builderConstructor.newInstance(httpClient);                    // Call the constructor
        final Method unsubscribeMethod = builderClass.getMethod(
                "withUnsubscribeUrl", String.class);                              // Find the method that needs a URI
        unsubscribeMethod.setAccessible(true);                                          // Make it callable
        unsubscribeMethod.invoke(builder, "http://localhost:8080");              // Call it with a valid URI
        return clazz.cast(objConstructor.newInstance(builder));                         // Call the constructor that takes the Builder as its argument
    }

    @SneakyThrows
    private SnsUnknownMessage makeUnknownMessage() {
        final Class<SnsUnknownMessage> clazz = SnsUnknownMessage.class;
        final Constructor<?> objConstructor = Arrays.stream(clazz.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1).findFirst().get();             // This is the constructor that takes a builder.
        objConstructor.setAccessible(true);                                             // Make it callable
        final Class<?> builderClass = Class.forName(clazz.getCanonicalName() + "$Builder");   // Get the Builder Class object
        final Constructor<?> builderConstructor = Arrays.stream(builderClass.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 0).findFirst().get();       // Get the Builder's no-arg constructor
        builderConstructor.setAccessible(true);                                         // Make it callable
        final Object builder = builderConstructor.newInstance();                              // Call it
        return clazz.cast(objConstructor.newInstance(builder));                         // Call the constructor that takes a Builder
    }

    @SneakyThrows
    private SnsUnsubscribeConfirmation makeUnsubscribeConfirmation() {
        final Class<SnsUnsubscribeConfirmation> clazz = SnsUnsubscribeConfirmation.class;
        final Constructor<?> objConstructor = Arrays.stream(clazz.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1).findFirst().get();             // This is the constructor that takes a builder.
        objConstructor.setAccessible(true);                                             // Make it callable.
        final Class<?> builderClass = Class.forName(clazz.getCanonicalName() + "$Builder");   // Get the Builder Class object.
        final Constructor<?> builderConstructor = Arrays.stream(builderClass.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1).findFirst().get();       // Get the constructor for the Builder that takes an HttpClient
        builderConstructor.setAccessible(true);                                         // Make it callable.
        final Object builder = builderConstructor.newInstance(httpClient);                    // Call it with the httpClient
        final Method unsubscribeMethod = builderClass.getMethod(
                "withSubscribeUrl", String.class);                                // Get the method that needs a URI
        unsubscribeMethod.setAccessible(true);                                          // Make it callable
        unsubscribeMethod.invoke(builder, "http://localhost:8080");              // Add the URI
        return clazz.cast(objConstructor.newInstance(builder));                         // Build the object using the Builder.
    }

    @SneakyThrows
    private SnsSubscriptionConfirmation makeSubscriptionConfirmation() {
        final Class<SnsSubscriptionConfirmation> clazz = SnsSubscriptionConfirmation.class;
        final Constructor<?> objConstructor = Arrays.stream(clazz.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1).findFirst().get();             // This is the constructor that takes a builder.
        objConstructor.setAccessible(true);                                             // Make it callable.
        final Class<?> builderClass = Class.forName(clazz.getCanonicalName() + "$Builder");   // Get the Builder Class object
        final Constructor<?> builderConstructor = Arrays.stream(builderClass.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1).findFirst().get();       // Get the constructor for the Builder that takes an HttpClient
        builderConstructor.setAccessible(true);                                         // Make it callable.
        final Object builder = builderConstructor.newInstance(httpClient);                    // Call the constructor to make a Builder.
        final Method unsubscribeMethod = builderClass.getMethod(
                "withSubscribeUrl", String.class);                                // Get the method that needs a URI
        unsubscribeMethod.setAccessible(true);                                          // Make it callable
        unsubscribeMethod.invoke(builder, "http://localhost:8080");              // Call it with a URI
        return clazz.cast(objConstructor.newInstance(builder));                         // Build the object using the Builder.
    }

    private SNSMessage makeSubscriptionMessage() {

        return SNSMessage.builder()
                .subject("Hello")
                .token("World")
                .signatureVersion("1")
                .message(Json.toJsonString("")).build();
    }

    private ComputeSpecification makeComputeSpecification() {
        return ComputeSpecification.ComputeSpecificationBuilder.buildWithDefaults();
    }

    private BatchJobDefinition makeBatchDefinition()
    {
        return BatchJobDefinition.builder()
                .jobDefinitionName("test-job")
                .jobAttempts(1)
                .computeSpecification(makeComputeSpecification())
                .build();
    }

    private ComponentDefinition makeComponentDefinition() {
        return ComponentDefinition.builder()
                .componentName("testComponent")
                .computeSpecification(makeComputeSpecification())
                .build();
    }

    private AppDefinition makeAppDefinition(final String portfolioVersion, final AppDefOptions options) {
        final String portfolioVersionToUse = ComputeStringOps.firstNonEmpty(portfolioVersion, "1.0.42");

        AppDefinition.AppDefinitionBuilder builder =
                AppDefinition.builder()
                        .appName("fpctest-c-uw2-sb")
                        .portfolioVersion(portfolioVersionToUse);
        if (options == AppDefOptions.INCLUDE_BATCH || options == AppDefOptions.INCLUDE_BOTH)
            builder = builder.batch(makeBatchDefinition());
        if (options == AppDefOptions.INCLUDE_COMPONENT || options == AppDefOptions.INCLUDE_BOTH)
            builder = builder.component(makeComponentDefinition());

        return builder.build();
    }

    private AppUpdate makeAppUpdateMessage(final ApiOperationType operationType, final String portfolioVersion, final AppDefinition app) {
        return AppUpdate.builder().operationType(operationType)
                .app(app).build();
    }

    @Test
    public void testUnsubscribe() {
        handler.handle(unsubscribeConfirmation);
        handler.handle(unknownMessage);
        Assert.assertTrue(true);    // Does nothing other than log; doesn't throw
    }

    @Test
    public void newSuccessfulDeployment() {
        final AppDefinition app = makeAppDefinition("1.0.42", AppDefOptions.INCLUDE_BOTH);
        final AppUpdate appUpdateMessage = makeAppUpdateMessage(
                ApiOperationType.CREATE_OR_UPDATE_DNS, "1.0.42", app);
        doNothing().when(dynamoDBSnsClient).update(any(AppUpdateMapper.class));
        when(notification.getMessage()).thenReturn(Json.toJsonString(appUpdateMessage));
        handler.handle(notification);
        // SyncResource is called to create the compute resources.
        verify(workerResolver, times(1)).addServiceResources(
                any(AppDefinition.class), any(ApiOperationType.class));
    }

    @Test
    public void newTestDeploymentComponent() throws ComputeException {
        final AppDefinition app = makeAppDefinition("1.0.42", AppDefOptions.INCLUDE_COMPONENT);
        final AppUpdate appUpdateMessage = makeAppUpdateMessage(
                ApiOperationType.CREATE_OR_UPDATE_APPLICATION, "1.0.42", app);
        when(notification.getMessage()).thenReturn(Json.toJsonString(appUpdateMessage));
        // Run the async code synchronously to guarantee it runs before the validation.
        when(handler.runAsynchronously()).thenReturn(false);
        doNothing().when(dynamoDBSnsClient).update(any(AppUpdateMapper.class));
        handler.handle(notification);
        // SyncResource is called to create the test resources.
        // getWorkerResources is called to force adf loading of the deployed adf for this moniker.
        verify(workerResolver, times(1)).addServiceResources(
                any(AppDefinition.class), any(ApiOperationType.class));
        verify(workerResolver, times(1)).getWorkerResources(anyString(), anyString(), isNull());
    }

    @Test
    public void newTestDeploymentBatch() throws ComputeException {
        final AppDefinition app = makeAppDefinition("1.0.42", AppDefOptions.INCLUDE_BATCH);
        final AppUpdate appUpdateMessage = makeAppUpdateMessage(
                ApiOperationType.CREATE_OR_UPDATE_APPLICATION, "1.0.42", app);
        when(notification.getMessage()).thenReturn(Json.toJsonString(appUpdateMessage));
        // Run the async code synchronously to guarantee it runs before the validation.
        when(handler.runAsynchronously()).thenReturn(false);
        doNothing().when(dynamoDBSnsClient).update(any(AppUpdateMapper.class));
        handler.handle(notification);
        // SyncResource is called to create the test resources.
        // getWorkerResources is called to force adf loading of the deployed adf for this moniker.
        verify(workerResolver, times(1)).addServiceResources(
                any(AppDefinition.class), any(ApiOperationType.class));
        verify(workerResolver, times(1)).getWorkerResources(anyString(), anyString(), isNull());
    }

    @Test
    public void subscribe() {
        handler.handle(subscriptionConfirmation);
        Assert.assertTrue(true);    // Should not throw
    }
}
