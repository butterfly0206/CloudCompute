package com.autodesk.compute.auth;

import com.autodesk.compute.auth.basic.BasicAuthSecurityContext;
import com.autodesk.compute.auth.basic.BasicCredentials;
import com.autodesk.compute.auth.oauth.OxygenSecurityContext;
import com.autodesk.compute.model.OAuthToken;
import com.google.common.collect.ImmutableSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class TestBasicSecurityContext {
    @Mock
    private WorkerCredentials workerCredentials;


    @Before
    public void initialize() {
        MockitoAnnotations.openMocks(this);
//        when(context.getWorkerCredentials()).thenReturn(workerCredentials);
    }

    @Test
    public void disabledBasicAuthAlwaysWorks() {
        BasicAuthSecurityContext context = new BasicAuthSecurityContext(false, Optional.empty());
        Assert.assertEquals("Basic", context.getAuthenticationScheme());
        Assert.assertTrue(context.isSecure());
        Assert.assertFalse(context.isEnforceBasicAuth());
        Assert.assertTrue(context.isUserInRole("anyRole"));
        Assert.assertNull(context.getUserPrincipal());
        context = new BasicAuthSecurityContext(false,
                Optional.of(
                    new BasicCredentials("fred", "flintstone")));
        Assert.assertTrue(context.isSecure());
        Assert.assertFalse(context.isEnforceBasicAuth());
        Assert.assertTrue(context.isUserInRole("anyRole"));
        Assert.assertEquals("fred", context.getUserPrincipal().getName());
    }

    @Test
    public void enabledAuthNoUser() {
        final BasicAuthSecurityContext context = new BasicAuthSecurityContext(true, Optional.empty());
        Assert.assertFalse(context.isSecure());
        Assert.assertFalse(context.isUserInRole("anyRole"));
        Assert.assertNull(context.getUserPrincipal());
    }

    @Test
    public void enabledAuthUserNotFound() {
        final BasicAuthSecurityContext context = new BasicAuthSecurityContext(true,
                Optional.of(
                        new BasicCredentials("fred", "flintstone")));
        final BasicAuthSecurityContext contextSpy = spy(context);
        when(contextSpy.getWorkerCredentials()).thenReturn(workerCredentials);
        when(workerCredentials.isAuthorized(anyString(), anyString())).thenReturn(false);
        Assert.assertFalse(contextSpy.isSecure());
        Assert.assertFalse(contextSpy.isUserInRole("anyRole"));
        // Should this be null?
        Assert.assertEquals("fred", contextSpy.getUserPrincipal().getName());
    }

    @Test
    public void enabledAuthUserFound() {
        final BasicAuthSecurityContext context = new BasicAuthSecurityContext(true,
                Optional.of(
                        new BasicCredentials("fred", "flintstone")));
        final BasicAuthSecurityContext contextSpy = spy(context);
        when(contextSpy.getWorkerCredentials()).thenReturn(workerCredentials);
        when(workerCredentials.isAuthorized(anyString(), anyString())).thenReturn(true);
        Assert.assertTrue(contextSpy.isSecure());
        Assert.assertTrue(contextSpy.isUserInRole("anyRole"));
        Assert.assertEquals("fred", contextSpy.getUserPrincipal().getName());
    }

    @Test
    public void getVariousSecurityContextTypes() {
        final BasicAuthSecurityContext context = new BasicAuthSecurityContext(true,
                Optional.of(
                        new BasicCredentials("fred", "flintstone")));

        BasicAuthSecurityContext received = BasicAuthSecurityContext.get(context);
        Assert.assertEquals(context, received);
        final OxygenSecurityContext oxygenContext = new OxygenSecurityContext(
                ImmutableSet.of("testGatewaySecretV1", "testGatewaySecretV2"
), false, OAuthToken.builder().build());
        received = BasicAuthSecurityContext.get(oxygenContext);
        Assert.assertNotEquals(received, oxygenContext);

        // This assert depends on the test configuration of the basic auth enabled value, so it could be fragile.
        Assert.assertTrue(received.isSecure());
    }
}
