package com.autodesk.compute.auth.basic;

import com.autodesk.compute.configuration.ComputeConfig;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Optional;

@Provider
@OptionalBasicAuth
@Slf4j
public class OptionalBasicAuthRequestFilter implements ContainerRequestFilter {
    public static final String AUTHORIZATION_HEADER = "Authorization";

    private final ComputeConfig conf;

    public OptionalBasicAuthRequestFilter(final ComputeConfig conf) {
        this.conf = conf;
    }

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        final String authHeader = requestContext.getHeaderString(AUTHORIZATION_HEADER);
        final Optional<BasicCredentials> credentials = BasicCredentials.fromHeader(authHeader);
        final BasicAuthSecurityContext securityContext = new BasicAuthSecurityContext(conf.isEnforceWorkerAuthentication(), credentials);
        if (!securityContext.isSecure()) {
            throw new NotAuthorizedException("Unvalidated Basic authorization");
        }
        requestContext.setSecurityContext(securityContext);
    }
}
