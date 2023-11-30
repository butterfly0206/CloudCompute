package com.autodesk.compute.auth.oauth;

import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.OAuthTokenUtils;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.model.OAuthToken;
import jakarta.ws.rs.core.SecurityContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import java.security.Principal;
import java.util.Set;

@Slf4j
public class OxygenSecurityContext implements SecurityContext {
    @Getter
    private final Set<String> apigeeSecrets;
    @Getter
    private final OAuthToken tokenData;
    @Getter
    private final boolean apigeeEnforce;

    public OxygenSecurityContext(final Set<String> apigeeSecrets, final boolean enforceApigee, final OAuthToken tokenData) {
        this.apigeeSecrets = apigeeSecrets;
        this.apigeeEnforce = enforceApigee;
        this.tokenData = tokenData;
    }

    @Override
    public Principal getUserPrincipal() {
        if (this.tokenData == null || !isSecure())
            return null;
        // Principal is a single-method interface, so we can return a lambda.
        return () -> {
            final String userId = OxygenSecurityContext.this.getUserId();
            if (ComputeStringOps.isNullOrEmpty(userId))
                return OxygenSecurityContext.this.getClientId();
            return userId;
        };
    }

    @Override
    public boolean isUserInRole(final String role) {
        return OAuthTokenUtils.hasScope(tokenData, role);
    }

    @Override
    public boolean isSecure() {
        // Don't enforce security if we have no local apigee secret
        if (apigeeSecrets == null || apigeeSecrets.isEmpty() || !apigeeEnforce) {
            log.info("isSecure true: apigee enforcement disabled or no secret configured");
            return true;
        }
        // If we have an apigee secret but no token data, fail.
        if (this.tokenData == null) {
            log.info("isSecure false: no x-ads-token-data present");
            return false;
        }
        // If we have one, but the caller didn't, or they don't match,
        // then they're not authorized.
        if (ComputeStringOps.isNullOrEmpty(tokenData.getApigeeSecret()) || !apigeeSecrets.contains(tokenData.getApigeeSecret())) {
            log.info("isSecure false: apigee secret was absent or wrong");
            return false;
        }
        // Check various elements that should exist. Clientid is there for both two- and three-legged.
        if (ComputeStringOps.isNullOrEmpty(tokenData.getClientId())) {
            log.info("isSecure false: no clientid in request");
            return false;
        }
        if (tokenData.getAccessToken() == null) {
            log.info("isSecure false: no access token in request");
            return false;
        }
        // It passed the basic checks. If the token is three-legged,
        // then there's a userid in the AccessToken;    //NOSONAR
        // For two-legged the client id is the only thing there.
        log.info("isSecure true: all basic checks completed");
        return true;
    }


    public String getClientId() {
        return getTokenData() != null ? getTokenData().getClientId() : null;
    }

    public String getApplicationId() {
        return getTokenData() != null ? getTokenData().getAppId() : null;
    }

    public String getBearerToken() {
        return getTokenData() != null ? getTokenData().getBearerToken() : null;
    }

    public String getUserId() {
        if (getTokenData() != null && getTokenData().getAccessToken() != null)
            return getTokenData().getAccessToken().getUserId();
        return null;
    }

    @Override
    public String getAuthenticationScheme() {
        return "Apigee-OAuth2";
    }

    public static OxygenSecurityContext get(final SecurityContext securityContext) {
        if (securityContext instanceof OxygenSecurityContext) {
            log.debug("getOxygenSecurityContext: Using passed in OxygenSecurityContext");
            return (OxygenSecurityContext) securityContext;
        }
        log.debug("getOxygenSecurityContext: Using session OxygenSecurityContext, passed in context is null or not an O2 context");
        return OxygenSecurityContext.get();
    }

    public static OxygenSecurityContext get() {
        final SecurityContext context = ResteasyProviderFactory.getInstance().getContextData(SecurityContext.class);
        if (context instanceof OxygenSecurityContext)
            return (OxygenSecurityContext) context;
        return new OxygenSecurityContext(ComputeConfig.getInstance().getOxygenReqFilterConfiguration().getApigeeSecrets(), ComputeConfig.getInstance().getOxygenReqFilterConfiguration().enforceApigee(), null);
    }
}
