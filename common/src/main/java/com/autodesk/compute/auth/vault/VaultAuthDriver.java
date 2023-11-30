package com.autodesk.compute.auth.vault;

import com.autodesk.compute.configuration.VaultConfiguration;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VaultAuthDriver extends VaultCache {
    @Value
    public static class Configuration {
        private VaultConfiguration vaultConfiguration;
    }

    public VaultAuthDriver(final VaultAuthDriver.Configuration cts) {
        super(cts.getVaultConfiguration());
    }

    /**
     * - simple wrapper around a token validation
     *
     * @param token - Vault token
     * @return true if the token is authenticated, else false
     */
    public boolean isAuthenticated(final String token) {
        return validateToken(token);
    }

}

