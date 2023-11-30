package com.autodesk.compute.auth.basic;

import com.autodesk.compute.auth.WorkerCredentials;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.configuration.ComputeConfig;
import jakarta.ws.rs.core.SecurityContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import java.security.Principal;
import java.util.Optional;

@Slf4j
public class BasicAuthSecurityContext implements SecurityContext {
    @Getter
    private final Optional<BasicCredentials> credentials;
    @Getter
    private final boolean enforceBasicAuth;

    public BasicAuthSecurityContext(final boolean enforceBasicAuth, final Optional<BasicCredentials> credentials) {
        this.enforceBasicAuth = enforceBasicAuth;
        this.credentials = credentials;
    }

    @Override
    public Principal getUserPrincipal() {
        if (!this.credentials.isPresent())
            return null;
        // Principal is a single-method interface, so we can return a lambda.
        return () ->
            BasicAuthSecurityContext.this.credentials.get().getUserName();
    }

    @Override
    public boolean isUserInRole(final String role) {
        return isSecure();
    }

    @Override
    public boolean isSecure() {
        // Don't enforce security if we have it optional
        boolean resultIfChecksFail = false;
        if (!enforceBasicAuth) {
            log.info("isSecure : enforceBasicAuth flag is False");
            resultIfChecksFail = true;
        }
        // But validate everything - run the password checks and so on, and write the metrics.
        if (!credentials.isPresent()) {
            log.info("isSecure: Authentication Credentials are not present");
            Metrics.getInstance().reportNoAuthHeader();
            return resultIfChecksFail;
        }
        final boolean passwordCheckResult = credentials.map(creds ->
                getWorkerCredentials().isAuthorized(creds.getUserName(), creds.getPassword())
        ).orElse(false);
        return passwordCheckResult || resultIfChecksFail;
    }

    // Split out this method so it can be mocked
    public WorkerCredentials getWorkerCredentials() {
        return WorkerCredentials.getInstance();
    }

    @Override
    public String getAuthenticationScheme()
    {
        return "Basic";
    }

    public static BasicAuthSecurityContext get(final SecurityContext securityContext) {
        if (securityContext instanceof BasicAuthSecurityContext) {
            log.debug("BasicAuthSecurityContext: Using passed in OxygenSecurityContext");
            return (BasicAuthSecurityContext) securityContext;
        }
        log.debug("BasicAuthSecurityContext: Using session BasicAuthSecurityContext, passed in context is null or not a basic auth context");
        return BasicAuthSecurityContext.get();
    }

    public static BasicAuthSecurityContext get() {
        final SecurityContext context = ResteasyProviderFactory.getInstance().getContextData(SecurityContext.class);
        if (context instanceof BasicAuthSecurityContext)
            return (BasicAuthSecurityContext) context;
        return new BasicAuthSecurityContext(ComputeConfig.getInstance().isEnforceWorkerAuthentication(), Optional.empty());
    }

}
