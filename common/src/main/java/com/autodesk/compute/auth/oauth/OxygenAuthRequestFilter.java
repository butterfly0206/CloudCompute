package com.autodesk.compute.auth.oauth;

import com.autodesk.compute.common.OAuthTokenUtils;
import com.autodesk.compute.model.OAuthToken;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.Set;

/**
 * Created by wattd on 6/1/17.
 * Used by CloudOS Compute
 */
@Provider
@OxygenAuth
public class OxygenAuthRequestFilter implements ContainerRequestFilter {
    private final Configuration conf;

    public interface Configuration {
        Set<String> getApigeeSecrets();
        boolean enforceApigee();
    }

    public OxygenAuthRequestFilter(final Configuration conf) {
        this.conf = conf;
    }

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        final OAuthToken tokenData = OAuthTokenUtils.from(requestContext.getHeaders());
        final OxygenSecurityContext securityContext = new OxygenSecurityContext(conf.getApigeeSecrets(), conf.enforceApigee(), tokenData);
        if (!securityContext.isSecure() ) {
            throw new NotAuthorizedException("Unvalidated OAuth2 authorization");
        }
        requestContext.setSecurityContext(securityContext);
    }
}
