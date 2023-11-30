package com.autodesk.compute.auth.vault;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.configuration.VaultConfiguration;
import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.google.common.cache.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * - CloudOS Vault Driver Wrapper with internal cache of vault-java-driver instances per token.
 * - It will also internally evict entries that have passed their token ttls.
 *
 * @author mokl
 */
@Slf4j
public class VaultCache extends VaultBase {
    // - substring length to display of keys (vault tokens)
    private static final int displayLength = 4;

    // - token id to vault driver cache (there is a driver per token)
    private final LoadingCache<String, CloudosVault> vaultCache;
    // - token id to long ttl cache; will be coupled with above in constructor
    private final Cache<String, Long> ttlCache;

    private final String vaultAddr;
    private final int retries;
    private final int cooldown;

    /**
     * - convenience method for using a Constants instance for configuration instead
     *
     * @param cts
     */
    public VaultCache(final VaultConfiguration cts) {
        this(cts.getVaultAddr(), cts.getVaultCacheSize(), cts.getVaultCacheExpiryTime(),
                cts.getVaultCacheExpiryUnit(), cts.getVaultOpRetries(), cts.getVaultOpCooldown());
    }

    private CloudosVault getCloudosVault(final String token) throws VaultException {
        final VaultConfig cfg = new VaultConfig()
                .address(vaultAddr)
                .token(token)
                .sslConfig(new SslConfig().verify(true))
                .build();
        return new CloudosVault(cfg, retries, cooldown);
    }

    /**
     * - initialises the vault driver with a cache for keys and one for ttls
     * - must specify vault location and specifications for the caches (note: expiryTime x expiryUnit
     * is the limit at which tokens expire)
     * - per-token ttls is managed internally and will evict expired tokens even if the
     * expiryTime x expiryUnit isn't up yet
     *
     * @param vaultAddr
     * @param size
     * @param expiryTime
     * @param expiryUnit
     */
    public VaultCache(final String vaultAddr, final long size, final long expiryTime, final TimeUnit expiryUnit, final int retries, final int cooldown) {
        this.vaultAddr = vaultAddr;
        this.retries = retries;
        this.cooldown = cooldown;
        log.info("Vault cache: vault address = {}", vaultAddr);
        //
        // - mirroring ttl cache to track token-specific expiration times
        // - couples a ttl removal with a driver removal
        //
        this.ttlCache = CacheBuilder.newBuilder()
                .maximumSize(size)
                .expireAfterAccess(expiryTime, expiryUnit)
                .removalListener(new RemovalListener<String, Long>() {
                    @Override
                    public void onRemoval(final RemovalNotification<String, Long> removal) {
                        log.info("ttl for key {} evicted", removal.getKey().substring(0, displayLength));
                        vaultCache.invalidate(removal.getKey());
                    }
                })
                .build();

        //
        // - actual cache for vault drivers
        //
        this.vaultCache = CacheBuilder.newBuilder()

                //
                // - default max size and time for evictions
                //
                .maximumSize(size)
                .expireAfterAccess(expiryTime, expiryUnit)

                //
                // - add a listener to warn when keys are evicted
                // - couples a driver removal with a ttl removal
                //
                .removalListener((RemovalListener<String, CloudosVault>) removal -> {
                    log.info("vault driver for key {} evicted", removal.getKey().substring(0, displayLength));
                    ttlCache.invalidate(removal.getKey());
                })

                //
                // - add a loader that automatically provisions new vault drivers for a token
                // - in the case provisioning fails, handle with our custom handler
                //
                .build(new CacheLoader<>() {
                    @Override
                    public CloudosVault load(final String token) throws VaultException {
                        final CloudosVault vault = getCloudosVault(token);

                        //
                        // - validate token and get its ttl if available; find expiry time in seconds
                        //
                        final LookupResponse resp = vault.customized().lookupSelf();
                        ttlCache.put(token, System.currentTimeMillis() / 1000L + resp.getTtl());
                        return vault;
                    }
                });
        log.info("Vault driver initialized at VAULT_ADDR={}", vaultAddr);
    }

    /**
     * - helper to remove a token from both caches
     *
     * @param token
     */
    private void invalidate(final String token) {
        for (final Cache<String, ?> cache : Arrays.asList(this.vaultCache, this.ttlCache)) {
            cache.invalidate(token);
        }
    }

    /**
     * - map problematic situations to a correct response and a cloudos exception
     *
     * @param token
     * @param e
     * @param revoke
     * @return
     */
    private ComputeException vaultExceptionHandler(final String token, final VaultException e, final boolean revoke) {
        switch (e.getHttpStatusCode()) {
            case 0:
            case 400:
                return ComputeException.builder()
                        .code(ComputeErrorCodes.VAULT_ERROR)
                        .message("Vault request failed with httpStatusCode=" + e.getHttpStatusCode() + " due to VAULT_BAD_REQUEST, error=" + e.toString())
                        .cause(e)
                        .build();
            case 401:
            case 403:
                if (revoke) {
                    invalidate(token);
                }
                return ComputeException.builder()
                        .code(ComputeErrorCodes.VAULT_ERROR)
                        .message("Vault request failed with httpStatusCode=" + e.getHttpStatusCode() + " due to VAULT_UNAUTHORIZED, error=" + e.toString())
                        .cause(e)
                        .build();
            case 404:
                return ComputeException.builder()
                        .code(ComputeErrorCodes.VAULT_ERROR)
                        .message("Vault path not found, error=" + e.toString())
                        .cause(e)
                        .build();
            default: {
                log.error("VaultExceptionHandler - unusual exception received, httpStatusCode=" + e.getHttpStatusCode() + " error=" + e.toString(), e);
                return ComputeException.builder()
                        .code(ComputeErrorCodes.VAULT_ERROR)
                        .message("Vault request failed with httpStatusCode=" + e.getHttpStatusCode() + " due to error=" + e.toString())
                        .cause(e)
                        .build();
            }
        }
    }

    /**
     * - retrieve a driver instance for a token; will automatically revoke the token
     * if the ttl has expired
     *
     * @param token
     * @return
     */

    @SneakyThrows(ComputeException.class)
    private CloudosVault getDriverForToken(final String token) {
        try {

            //
            // - try and get the driver for the token; if it's there but the ttl has expired, invalidate it
            // - note that the expiry is system time in _seconds_, so convert accordingly
            //
            final Long exp = this.ttlCache.getIfPresent(token);
            if (exp != null && exp < System.currentTimeMillis() / 1000L) {
                invalidate(token);
            }
            return this.vaultCache.get(token);
        } catch (final ExecutionException e) {

            //
            // - an exception here means that the Vault instance couldn't be initialised
            // - map it to the correct CloudosException
            //
            if (e.getCause() instanceof VaultException) {
                throw this.vaultExceptionHandler(token, (VaultException) e.getCause(), true);
            } else {
                throw ComputeException.builder()
                        .code(ComputeErrorCodes.SERVER_UNEXPECTED)
                        .message("Failed to get driver for token due to error=" + e.toString())
                        .cause(e)
                        .build();
            }
        }
    }

    /**
     * - simple helper to lookup the data associated with a token
     *
     * @param token
     * @param vault
     * @return
     */
    @SneakyThrows(ComputeException.class)
    private LookupResponse lookupSelf(final String token, final CloudosVault vault) {
        try {
            return vault.customized().lookupSelf();
        } catch (final VaultException e) {
            throw vaultExceptionHandler(token, e, true);
        }
    }

    /**
     * - basic function for validating vault tokens with token caching by ttl; will _not_ throw if unauthorised
     *
     * @param token
     * @return
     */
    public boolean validateToken(final String token) {
        final CloudosVault vault = this.getDriverForToken(token);
        return lookupSelf(token, vault) != null;
    }

}
