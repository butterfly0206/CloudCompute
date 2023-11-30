package com.autodesk.compute.discovery;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.ListSubscriptionsByTopicRequest;
import com.amazonaws.services.sns.model.ListSubscriptionsByTopicResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class TestSnsSubscriptionManager {
    @Mock
    private AmazonSNS sns;

    @Spy
    private SnsSubscriptionManager subscriptionManager;

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSubscribeWhenUnsubscribed() {
        doReturn(sns).when(subscriptionManager).makeSNSClient();
        final ListSubscriptionsByTopicResult subscriptionsByTopicResult = new ListSubscriptionsByTopicResult()
                .withSubscriptions(Collections.emptyList());
        doReturn(subscriptionsByTopicResult).when(sns).listSubscriptionsByTopic(any(ListSubscriptionsByTopicRequest.class));
        doReturn(false).when(subscriptionManager).isSubscribedToSNSTopic();
        subscriptionManager.subscribeToADFUpdateSNSTopic();
        verify(sns, times(1)).listSubscriptionsByTopic(any(ListSubscriptionsByTopicRequest.class));
        verify(sns, times(1)).subscribe(any(SubscribeRequest.class));
    }


}
