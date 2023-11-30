package com.autodesk.compute.auth.vault;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Created by wattd on 6/1/17.
 */
@AllArgsConstructor
@Provider
@VaultAuth
@Slf4j
public class VaultAuthRequestFilter implements ContainerRequestFilter {
    private final VaultAuthDriver driver;

    @Override
    public void filter(final ContainerRequestContext requestContext) {
        final String vaultTokenHeader = requestContext.getHeaderString("x-vault-token");
        if (StringUtils.isEmpty(vaultTokenHeader)) {
            throw new NotAuthorizedException("No Vault token supplied");
        }
        try {
            if (!driver.isAuthenticated(vaultTokenHeader)) {
                log.warn("VaultAuthRequestFilter vault token supplied, but isAuthenticated returned false for token starting with {}",
                        vaultTokenHeader.length() < 4 ? vaultTokenHeader : vaultTokenHeader.substring(0, 4));
                throw new NotAuthorizedException("Vault token is not authorized");
            }
        } catch (final Exception e) {
            log.error("VaultAuthRequestFilter", e);
            throw new NotAuthorizedException(e);
        }
    }
}
