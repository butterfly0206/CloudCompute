package com.autodesk.compute.auth.vault;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import static com.google.common.base.MoreObjects.firstNonNull;

@AllArgsConstructor
@Provider
@OptionalVaultAuth
@Slf4j
public class OptionalVaultAuthRequestFilter implements ContainerRequestFilter {
    private final VaultAuthDriver driver;

    @Override
    public void filter(final ContainerRequestContext requestContext) {
        final String vaultTokenHeader = firstNonNull(requestContext.getHeaderString("x-vault-token"), "");
        final String tokenSnippet = vaultTokenHeader.length() < 4 ? vaultTokenHeader : vaultTokenHeader.substring(0, 4);
        try {
            if (StringUtils.isNotEmpty(vaultTokenHeader) && !driver.isAuthenticated(vaultTokenHeader)) {
                log.warn("OptionalVaultAuthRequestFilter vault token supplied, but isAuthenticated returned false for token starting with {}",
                        tokenSnippet);
                throw new NotAuthorizedException("Vault token is not authorized");
            }
        } catch (final Exception e) {
            log.error("OptionalVaultAuthRequestFilter: {} on token starting with {}", e.getMessage(), tokenSnippet);
            throw new NotAuthorizedException(e);
        }
    }
}