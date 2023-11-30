package com.autodesk.compute.api.impl;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.message.*;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.discovery.RedisCacheLoader;
import com.autodesk.compute.discovery.ServiceDiscoveryConfig;
import com.autodesk.compute.discovery.WorkerResolver;
import com.autodesk.compute.dynamodb.DynamoDBSnsClient;
import com.autodesk.compute.model.AppUpdate;
import com.autodesk.compute.model.cosv2.ApiOperationType;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.autodesk.compute.model.cosv2.BatchJobDefinition;
import com.autodesk.compute.model.cosv2.ComponentDefinition;
import com.autodesk.compute.model.dynamodb.AppUpdateMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.pivovarit.function.ThrowingRunnable;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.common.ComputeUtils.firstSupplier;
import static com.google.common.base.MoreObjects.firstNonNull;

@Slf4j
public class AdfUpdateMessageHandler extends SnsMessageHandler {

    private final DynamoDBSnsClient dynamoDBSnsClient;

    @Inject
    public AdfUpdateMessageHandler() {
        this(new DynamoDBSnsClient());
    }

    public AdfUpdateMessageHandler(final DynamoDBSnsClient dynamoDBSnsClient) {
        this.dynamoDBSnsClient = dynamoDBSnsClient;
    }

    public AmazonSNS makeAmazonSNS() {
        return makeStandardClient(AmazonSNSClientBuilder.standard());
    }

    public WorkerResolver getWorkerResolver() {
        return WorkerResolver.getInstance();
    }

    public RedisCacheLoader makeRedisCacheLoader() {
        return RedisCacheLoader.makeInstance();
    }

    @Override
    @SneakyThrows({JsonProcessingException.class})
    public void handle(final SnsNotification notificationMessage) {
        final String message = notificationMessage.getMessage();
        final AppUpdate appUpdate = Json.mapper.readValue(message, AppUpdate.class);
        getWorkerResolver().addServiceResources(appUpdate.app, appUpdate.operationType);

        log.info("AppUpdateGet: Received ADF update for {} {} of type {} from SNS and sync done",
                appUpdate.app.getAppName(), appUpdate.app.getPortfolioVersion(), appUpdate.operationType);
        // If the message was a create or update of a newly deployed version of an application,
        // then it can go into the long cache.
        try (final RedisCacheLoader loader = makeRedisCacheLoader()) {
            loader.invalidateAppState(appUpdate.app.getAppName());
            if (appUpdate.operationType == ApiOperationType.CREATE_OR_UPDATE_APPLICATION) {
                // We don't currently know if the thing about which we received the notification
                // is a test deployment, or has been tested and deployed for real.
                // To figure that out, we have to call the GK, and ask it for the deployed version
                // of the application.

                // Put the new deployment in the cache as a test version. We'll know
                // that it's deployed when we receive the other notification type.
                loader.addTestApplication(appUpdate.app);
                asyncLoadDeployedAdf(appUpdate.app);
            } else if (appUpdate.operationType == ApiOperationType.CREATE_OR_UPDATE_DNS) {
                // Put the new deployment in the cache in two forms: the deployed and the
                // portfolio versioned.
                loader.addDeployedApplication(appUpdate.app);
            }
        }

        //send to dynamodb database so that other instances of JM can synchronize
        //expire the record after 1 day
        final long unixTime = System.currentTimeMillis() / 1_000L + 86_400;
        final AppUpdateMapper update = AppUpdateMapper.builder()
                .messageId(notificationMessage.getMessageId())
                .update(message)
                .ttl(unixTime)
                .build();
        dynamoDBSnsClient.update(update);
        log.info("AppUpdateGet: Received ADF update for {} {} of type {} from SNS and saved as {} in db for notification",
                appUpdate.app.getAppName(),
                appUpdate.app.getPortfolioVersion(),
                appUpdate.operationType, notificationMessage.getMessageId());
    }

    @Override
    public void handle(final SnsSubscriptionConfirmation message) {
        if (ServiceDiscoveryConfig.getInstance().getAdfUpdateSNSTopicARN().equals(message.getTopicArn())) {
            message.confirmSubscription();
        } else {
            log.error("AppUpdateGet: SubscriptionConfirmation came in for weird topic; ignoring: {}", message.getTopicArn());
        }
    }

    @Override
    public void handle(final SnsUnsubscribeConfirmation message) {
        log.error("AppUpdateGet: Subscription {} got deleted from the topic, for some reason...", message.getSubscribeUrl());
    }

    @Override
    public void handle(final SnsUnknownMessage message) {
        log.error("AppUpdateGet: Unknown message type (id = {}) from SNS", message.getMessageId());
    }

    public void asyncLoadDeployedAdf(final AppDefinition service) {
        // Find a worker to ask about.
        if (!service.hasComputeSpec())
            return;

        final String foundWorker = firstSupplier(
                () -> findAnyBatchWorker(service),
                () -> findAnyComputeComponent(service));
        if (foundWorker != null) {
            execute(ThrowingRunnable.unchecked(() ->
                            getWorkerResolver().getWorkerResources(
                                    service.getAppName(),
                                    foundWorker,
                                    null)
                    )
            );
        }
    }

    // This is here so the tests can run the async code synchronously. cf the execute method below.
    public boolean runAsynchronously() {
        return true;
    }

    public void execute(final Runnable r) {
        if (runAsynchronously()) {
            CompletableFuture.runAsync(r);
        } else {
            r.run();
        }
    }
    public String findAnyBatchWorker(final AppDefinition service) {
        if (service.getBatches() == null)
            return null;
        // Find a batch worker with a compute spec.
        // At the moment, they *all* have one, but we're being paranoid.
        final BatchJobDefinition oneBatch = service.getBatches().stream()
                .filter(batch -> batch.getComputeSpecification() != null)
                .findFirst().orElse(null);
        // This should also never happen, but...
        if (oneBatch == null)
            return null;
        // The worker names are optional, so fall back if they are not specified.
        return firstNonNull(
                oneBatch.getComputeSpecification().getWorkerName(),
                oneBatch.getJobDefinitionName());
    }

    public String findAnyComputeComponent(final AppDefinition service) {
        final ComponentDefinition foundComponent = service.getComponents().stream()
                .filter(comp -> comp.getComputeSpecification() != null)
                .findFirst().orElse(null);
        if (foundComponent == null)
            return null;
        return firstNonNull(
                foundComponent.getComputeSpecification().getWorkerName(),
                foundComponent.getComponentName());
    }
}
