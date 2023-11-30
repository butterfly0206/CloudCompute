package com.autodesk.compute.configuration;

import lombok.Builder;
import lombok.Getter;

import java.util.concurrent.TimeUnit;

/**
 * Created by t_gomesa on 2018-11-15.
 */
@Getter
@Builder
public class VaultConfiguration {
    private final String cloudosMoniker;
    private final String vaultAddr;
    private final long vaultCacheSize;
    private final long vaultCacheExpiryTime;
    private final TimeUnit vaultCacheExpiryUnit;
    private final int vaultOpRetries;
    private final int vaultOpCooldown;
}
