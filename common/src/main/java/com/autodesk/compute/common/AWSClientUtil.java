package com.autodesk.compute.common;

//  Copyright (c) 2019 by Autodesk, Inc.
//  All rights reserved.
//
//  The information contained herein is confidential and proprietary to
//  Autodesk, Inc., and considered a trade secret as defined under civil
//  and criminal statutes.  Autodesk shall pursue its civil and criminal
//  remedies in the event of unauthorized use or misappropriation of its
//  trade secrets.  Use of this information by anyone other than authorized
//  employees of Autodesk, Inc. is granted only under a written non-
//  disclosure agreement, expressly prescribing the scope and manner of
//  such use.
//


import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.client.builder.AwsSyncClientBuilder;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.RetryPolicy;
import lombok.experimental.UtilityClass;
import net.jodah.typetools.TypeResolver;

@UtilityClass
public final class AWSClientUtil {
    private static final int DEFAULT_MAX_RETRIES = 7;
    private static final int DEFAULT_BASE_DELAY_MS = 500;
    private static final int DEFAULT_MAX_BACKOFF_MS = 60_000;
    private static final ClientConfiguration DEFAULT_CLIENT_CONFIGURATION = new ClientConfiguration()
            .withRetryPolicy(new RetryPolicy(
                    new AWSRetryCondition(),
                    new PredefinedBackoffStrategies.EqualJitterBackoffStrategy(DEFAULT_BASE_DELAY_MS, DEFAULT_MAX_BACKOFF_MS),
                    DEFAULT_MAX_RETRIES,
                    true));

    public static ClientConfiguration getDefaultClientConfigurationWithRetriesAndJitter() {
        return DEFAULT_CLIENT_CONFIGURATION;
    }

    public static <U extends AwsClientBuilder<U, T>, T> T makeStandardClient(final AwsClientBuilder<U, T> builder,
                                                                             final Class<T> targetClass,
                                                                             final AWSCredentials credentials,
                                                                             final String region) {

        builder.withClientConfiguration(getDefaultClientConfigurationWithRetriesAndJitter());
        if (!ComputeStringOps.isNullOrEmpty(region))
            builder.withRegion(region);
        if (credentials != null)
            builder.withCredentials(new AWSStaticCredentialsProvider(credentials));
        return targetClass.cast(builder.build());
    }

    public static <U extends AwsClientBuilder<U, T>, T> T makeStandardClient(final AwsClientBuilder<U, T> builder, final Class<T> targetClass) {
        return makeStandardClient(builder, targetClass, null, null);
    }

    public static <U extends AwsSyncClientBuilder<U, T>, T> T makeStandardClient(final AwsSyncClientBuilder<U, T> builder) {
        return makeStandardClient(builder, null, null);
    }

    public static <U extends AwsSyncClientBuilder<U, T>, T> T makeStandardClient(final AwsSyncClientBuilder<U, T> builder, final AWSCredentials credentials, final String region) {
        final Class<?>[] args = TypeResolver.resolveRawArguments(AwsClientBuilder.class, builder.getClass());
        return makeStandardClient(builder, (Class<T>) args[1], credentials, region);
    }
}
