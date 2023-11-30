package com.autodesk.compute.discovery;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.ListSubscriptionsByTopicRequest;
import com.amazonaws.services.sns.model.ListSubscriptionsByTopicResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.Subscription;
import com.autodesk.compute.configuration.ComputeConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;

@Slf4j
public class SnsSubscriptionManager {
    private final ServiceDiscoveryConfig serviceDiscoveryConfig;

    @Getter
    private final boolean subscribedToSNSTopic;

    @Inject
    public SnsSubscriptionManager() {
        serviceDiscoveryConfig = ServiceDiscoveryConfig.getInstance();
        subscribedToSNSTopic = !ComputeConfig.isCosV3() && subscribeToADFUpdateSNSTopic();
    }

    public AmazonSNS makeSNSClient() {
        return makeStandardClient(AmazonSNSClientBuilder.standard());
    }

    /**
     * Subscribe to SNS ADF update topic and create subscriber if not present
     */
    public boolean subscribeToADFUpdateSNSTopic() {
        Optional<Subscription> subscription = Optional.empty();
        try {
            if (this.isSubscribedToSNSTopic())
                return true;
            // Create a client
            final AmazonSNS snsClient = makeSNSClient();

            //Check SNS subscription exists or not, returns 100 subscription list
            final ListSubscriptionsByTopicRequest req = new ListSubscriptionsByTopicRequest();
            req.setTopicArn(serviceDiscoveryConfig.getAdfUpdateSNSTopicARN());
            final ListSubscriptionsByTopicResult subRes = snsClient.listSubscriptionsByTopic(req);
            final List<Subscription> list = subRes.getSubscriptions();
            // If a subscription has not been confirmed, then the subscriptionArn will have "Pending Confirmation"
            // instead of an actual ARN - which is why the subscriptionArn check is here.
            subscription = list.stream().filter(
                    oneSubscription -> oneSubscription.getProtocol().equals("https") &&
                            oneSubscription.getSubscriptionArn().startsWith("arn:") &&
                            oneSubscription.getEndpoint().equals(serviceDiscoveryConfig.getAdfSubscriptionEndpoint())
            ).findFirst();

            if (subscription.isEmpty()) {
                final URL url = new URL(serviceDiscoveryConfig.getAdfSubscriptionEndpoint());

                final SubscribeRequest subscribeReq = new SubscribeRequest()
                        .withTopicArn(serviceDiscoveryConfig.getAdfUpdateSNSTopicARN())
                        .withProtocol(url.getProtocol())
                        .withEndpoint(serviceDiscoveryConfig.getAdfSubscriptionEndpoint());

                snsClient.subscribe(subscribeReq);
                log.info("subscribeToADFUpdateSNSTopic: Subscribed to ADF update SNS topic: {}, subscription url {}, subscription arn {}",
                        serviceDiscoveryConfig.getAdfUpdateSNSTopicARN(),
                        serviceDiscoveryConfig.getAdfSubscriptionEndpoint(),
                        subscription.map(Subscription::getSubscriptionArn).orElse("subscription not found"));
            } else {
                log.info("subscribeToADFUpdateSNSTopic: Already subscribed to ADF update SNS topic: {}, subscription url {}, subscription arn {}",
                        serviceDiscoveryConfig.getAdfUpdateSNSTopicARN(),
                        serviceDiscoveryConfig.getAdfSubscriptionEndpoint(),
                        subscription.map(Subscription::getSubscriptionArn).orElse("subscription not found"));
            }

            return true;
        } catch (final Exception e) {
            log.error("subscribeToADFUpdateSNSTopic:", e);
            return false;
        }
    }
}
