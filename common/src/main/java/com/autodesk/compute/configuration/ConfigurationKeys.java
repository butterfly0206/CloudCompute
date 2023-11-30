package com.autodesk.compute.configuration;

import lombok.experimental.UtilityClass;

/**
 * Created by t_gomesa on 2018-11-16.
 */
@UtilityClass
public final class ConfigurationKeys {  //NOPMD

    // CloudOS Configuration
    // Prefix for the location of secrets in parameter store
    public static final String SECRETS_PARAMETERS_PATH = "secretsParametersPath";
    public static final String CLOUDOS_MONIKER = "cloudos_moniker";
    public static final String COS_MONIKER = "cos_moniker";
    public static final String REGION = "region";

    // TODO: what is this used for?
    public static final String OVERRIDES_AWS_ACCESS_KEY = "overrides.aws_access_key_id";
    public static final String OVERRIDES_AWS_SECRET_KEY = "overrides.aws_secret_access_key";
    public static final String OVERRIDES_AWS_ECSROLE_ARN = "overrides.aws_ecs_role_arn";

    // Vault Client Configuration
    public static final String VAULT_VAULT_ADDR = "vault.vaultAddr";
    public static final String VAULT_CACHE_SIZE = "vault.cacheSize";
    public static final String VAULT_CACHE_EXPIRY_TIME = "vault.cacheExpiryTime";
    public static final String VAULT_CACHE_EXPIRY_UNIT = "vault.cacheExpiryUnit";
    public static final String VAULT_OP_RETRIES = "vault.opRetries";
    public static final String VAULT_OP_COOLDOWN = "vault.opCooldown";

    // Oxygen Auth configuration, used by CloudOS Compute
    public static final String APIGEE_ENFORCE = "apigee_enforce";
}
