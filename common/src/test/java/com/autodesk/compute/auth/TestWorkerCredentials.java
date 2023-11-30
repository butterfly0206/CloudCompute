package com.autodesk.compute.auth;

import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.MockMetrics;
import com.autodesk.compute.common.SSMUtils;
import com.autodesk.compute.configuration.ComputeConstants;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.VersionedSecret;
import com.autodesk.compute.test.TestHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class TestWorkerCredentials {

    @Mock
    private SSMUtils ssm;

    @Spy
    @InjectMocks
    private WorkerCredentials creds;

    @Before
    public void initialize() throws ComputeException {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void tryLoadCredentialsThrottlingError() throws ComputeException {
        when(ssm.getSecretsByPathAndLabel(anyString(), anyString())).thenThrow(
                new ComputeException(ComputeErrorCodes.CONFIGURATION, "throttling"));
        creds.tryLoadCredentials();
        Assert.assertFalse(creds.isAuthorized("any", "any"));
        Assert.assertTrue(creds.getCurrentSecret("anything-else").isEmpty());
    }

    private VersionedSecret makeVersionedSecret(final String secret, final SSMUtils.SecretType secretType)
    {
        return new VersionedSecret(
                Instant.now().toEpochMilli() / 1_000,
                secretType == SSMUtils.SecretType.CURRENT ? "2" : "1",
                secret,
                secretType.toString());
    }

    private Answer<Map<String, VersionedSecret>> makeSecretsAnswers(final String moniker)
    {
        return TestHelper.makeAnswer(
                Collections.singletonMap("/ComputeWorkerCredentials/" + moniker, makeVersionedSecret( moniker, SSMUtils.SecretType.CURRENT)),
                Collections.singletonMap("/ComputeWorkerCredentials/" + moniker, makeVersionedSecret(moniker + "-fallback", SSMUtils.SecretType.FALLBACK))
        );
    }

    @Test
    public void testCurrentAndFallbackAuth() throws ComputeException {
        when(ssm.getSecretsByPathAndLabel(anyString(), anyString()))
                .thenAnswer(makeSecretsAnswers("fpcexapp-c-uw2-sb"));
        creds.forceReload();
        Assert.assertTrue(creds.isAuthorized("fpcexapp-c-uw2-sb", "fpcexapp-c-uw2-sb"));
        Assert.assertTrue(creds.isAuthorized("fpcexapp-c-uw2-sb", "fpcexapp-c-uw2-sb-fallback"));
        Assert.assertFalse(creds.isAuthorized("fpccomp-c-uw2-sb", "user2"));
        final MockMetrics metrics = (MockMetrics) Metrics.getInstance();
        final Map<String, Collection<MetricDatum>> allMetrics = metrics.getAllCapturedMetrics();
        final Collection<MetricDatum> fallbackAuth = allMetrics.get(ComputeConstants.MetricsNames.WORKER_FALLBACK_AUTH_USED);
        final Collection<MetricDatum> authFailed = allMetrics.get(ComputeConstants.MetricsNames.WORKER_AUTH_FAILED);
        final Collection<MetricDatum> authSucceeded = allMetrics.get(ComputeConstants.MetricsNames.WORKER_AUTH_SUCCEEDED);
        Assert.assertTrue(fallbackAuth.stream().anyMatch(datum -> datum.getDimensions().stream().anyMatch(
                dimension -> dimension.getName().equals("Moniker") && dimension.getValue().equals("FPCEXAPP-C-UW2-SB"))));
        Assert.assertTrue(authSucceeded.stream().anyMatch(datum -> datum.getDimensions().stream().anyMatch(
                dimension -> dimension.getName().equals("Moniker") && dimension.getValue().equals("FPCEXAPP-C-UW2-SB"))));
        Assert.assertTrue(authFailed.stream().anyMatch(datum -> datum.getDimensions().stream().anyMatch(
                dimension -> dimension.getName().equals("Moniker") && dimension.getValue().equals("FPCCOMP-C-UW2-SB"))));
    }

}
