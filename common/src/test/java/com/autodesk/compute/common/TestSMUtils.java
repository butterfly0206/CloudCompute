package com.autodesk.compute.common;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.*;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.VersionedSecret;
import com.autodesk.compute.test.Matchers;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.autodesk.compute.test.TestHelper.makeAnswer;
import static com.autodesk.compute.test.TestHelper.validateThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Tests the V3 SecretsManager version of SSMUtils.
public class TestSMUtils {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Mock
    private AWSSecretsManager smClient;

    @Spy
    @InjectMocks
    private SSMUtils ssm;

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
    }

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void nullGetSecretByPath() {
        environmentVariables.set("COS_SECRETS_MANAGER_PATH", "null-return");
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.CONFIGURATION),
                () -> ssm.getSecretByPath("null-return"));
    }

    @Test
    public void getSecretByPathThrows() {
        environmentVariables.set("COS_SECRETS_MANAGER_PATH", "throws");
        when(smClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenThrow(new AWSSecretsManagerException("test"));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.CONFIGURATION),
                () -> ssm.getSecretByPath("throws"));
    }

    @Test
    public void nullGetSecretsByPath() {
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.CONFIGURATION),
                () -> ssm.getSecretsByPath("null-return"));
    }

    @Test
    public void nullGetSecretsByPathAndLabel() {
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.CONFIGURATION),
                () -> ssm.getSecretsByPathAndLabel("null-return", "value"));
    }

    @Test
    public void getSecretsByPathAndLabelThrows() {
        when(smClient.listSecrets(any(ListSecretsRequest.class)))
                .thenThrow(new AWSSecretsManagerException("test"));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.CONFIGURATION),
                () -> ssm.getSecretsByPathAndLabel("throws", "value"));
    }

    private ListSecretsResult makePageOfSecrets(final boolean hasNext) {
        ListSecretsResult result = new ListSecretsResult();
        if (hasNext)
            result.setNextToken("next-token");
        result = result.withSecretList(new SecretListEntry().withName("name-" + counter.incrementAndGet())
                .withSecretVersionsToStages(
                        Map.of("secretId", Lists.newArrayList("AWSCURRENT"))
                )
                .withLastChangedDate(Date.from(Instant.now())));
        return result;
    }

    @Test
    public void getSecretsByPathAndLabelHappyPath() throws ComputeException {
        when(smClient.listSecrets(any(ListSecretsRequest.class))).thenAnswer(
                makeAnswer(makePageOfSecrets(true), makePageOfSecrets(false)));
        when(smClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(new GetSecretValueResult()
                        .withName("secretId")
                        .withSecretString("{ \"key\": \"value\"}"));
        final Map<String, VersionedSecret> secrets = ssm.getSecretsByPathAndLabel("path", "label");
        Assert.assertEquals(2, secrets.size());
    }

    @Test
    public void getSecretsByPathHappyPath() throws ComputeException {
        when(smClient.listSecrets(any(ListSecretsRequest.class))).thenAnswer(
                makeAnswer(makePageOfSecrets(true), makePageOfSecrets(true), makePageOfSecrets(false)));
        when(smClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(new GetSecretValueResult()
                        .withName("secretId")
                        .withSecretString("{ \"key1\": \"value1\", \"key2\": \"value2\", \"key3\": \"value3\"}"));
        final Map<String, String> secrets = ssm.getSecretsByPath("path");
        Assert.assertEquals(3, secrets.size());
    }

}
