package com.autodesk.compute.common;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.model.*;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.VersionedSecret;
import com.autodesk.compute.test.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
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

public class TestSSMUtils {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Mock
    private AWSSimpleSystemsManagement ssmClient;

    @Spy
    @InjectMocks
    private SSMUtils ssm;

    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void nullGetSecretByPath() {
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.CONFIGURATION),
                () -> ssm.getSecretByPath("null-return"));
    }

    @Test
    public void getSecretByPathThrows() {
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
            .thenThrow(new AWSSimpleSystemsManagementException("test"));
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
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenThrow(new AWSSimpleSystemsManagementException("test"));
        validateThrows(
                ComputeException.class,
                new Matchers.ExceptionCodeMatcher(ComputeErrorCodes.CONFIGURATION),
                () -> ssm.getSecretsByPathAndLabel("throws", "value"));
    }

    private GetParametersByPathResult makePageOfSecrets(final boolean hasNext) {
        GetParametersByPathResult result = new GetParametersByPathResult();
        if (hasNext)
            result.setNextToken("next-token");
        result = result.withParameters(new Parameter().withName("name-" + counter.incrementAndGet())
                .withType("String").withValue("secret").withVersion(2L).withLastModifiedDate(Date.from(Instant.now())));
        return result;
    }

    @Test
    public void getSecretsByPathAndLabelHappyPath() throws ComputeException {
        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class))).thenAnswer(
                makeAnswer(makePageOfSecrets(true), makePageOfSecrets(false)));
        final Map<String, VersionedSecret> secrets = ssm.getSecretsByPathAndLabel("path", "label");
        Assert.assertEquals(2, secrets.size());
    }

    @Test
    public void getSecretsByPathHappyPath() throws ComputeException {
        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class))).thenAnswer(
                makeAnswer(makePageOfSecrets(true), makePageOfSecrets(true), makePageOfSecrets(false)));
        final Map<String, String> secrets = ssm.getSecretsByPath("path");
        Assert.assertEquals(3, secrets.size());
    }

}
