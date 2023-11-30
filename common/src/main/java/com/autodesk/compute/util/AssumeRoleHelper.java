package com.autodesk.compute.util;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.autodesk.compute.common.ComputeStringOps;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
public class AssumeRoleHelper {

    public static AWSCredentialsProvider getCrossAccountCredentialsProvider(final String accountId, final String region, final String executionMoniker /* i.e. fpccomp-c-uw2-sb */, final String appMoniker) {
        final AWSSecurityTokenService stsClient =
                AWSSecurityTokenServiceClientBuilder.standard()
                        .withRegion(region)
                        .build();
        final String roleArn = ComputeStringOps.isNullOrEmpty(appMoniker) ?
                String.format("arn:aws:iam::%s:role/%s-cross-account-execution-role", accountId, executionMoniker) :
                String.format("arn:aws:iam::%s:role/%s-%s-cross-account-execution-role", accountId, appMoniker, executionMoniker);

        final String sessionId = UUID.randomUUID().toString();
        return new STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, sessionId).withStsClient(stsClient).build();
    }
}
