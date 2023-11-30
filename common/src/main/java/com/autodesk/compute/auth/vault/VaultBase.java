package com.autodesk.compute.auth.vault;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.api.Auth;
import com.bettercloud.vault.api.Logical;
import com.bettercloud.vault.json.Json;
import com.bettercloud.vault.json.JsonObject;
import com.bettercloud.vault.json.JsonValue;
import com.bettercloud.vault.response.AuthResponse;
import com.bettercloud.vault.response.LogicalResponse;
import com.bettercloud.vault.response.VaultResponse;
import com.bettercloud.vault.rest.Rest;
import com.bettercloud.vault.rest.RestException;
import com.bettercloud.vault.rest.RestResponse;
import lombok.SneakyThrows;

import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * - Fixed, tuned, and extended the vault-java-driver (https://github.com/BetterCloud/vault-java-driver) library
 * for CloudOS requirements
 *
 * @author mokl
 */
class VaultBase {

    private static final String X_VAULT_TOKEN = "X-Vault-Token";
    private static final String POLICIES = "policies";
    private static final String TLS_1_2 = "TLSv1.2";
    private static final String VAULT_ERROR_MESSAGE = "Vault responded with HTTP status code: %s | error: %s";

    @SneakyThrows(InterruptedException.class)
    private static void retryIfPossible(final AtomicInteger retryCount, final VaultConfig vcfg, final Exception e) throws VaultException {
        // If there are retries to perform, then pause for the configured interval and then execute the loop again...
        if (retryCount.incrementAndGet() <= vcfg.getMaxRetries()) {
            final int retryIntervalMilliseconds = vcfg.getRetryIntervalMilliseconds();
            Thread.sleep(retryIntervalMilliseconds);
        } else {
            wrapAndRethrowAsVaultException(e);
        }
    }

    @SneakyThrows(InterruptedException.class)
    private static boolean sleepBeforeRetry(final int currentRetryCount, final int maxRetries, final int sleepIntervalMillis) {
        if (currentRetryCount < maxRetries) {
            Thread.sleep(sleepIntervalMillis);
            return true;
        }
        return false;
    }

    private static void wrapAndRethrowAsVaultException(final Exception caughtException) throws VaultException {
        if (caughtException instanceof VaultException)
            throw (VaultException) caughtException;
        throw new VaultException(caughtException);
    }

    /**
     * - response type specifically for token and meta lookups
     * - extends the base VaultResponse class provided by the vault-java-driver package
     * - does _not_ use lombok on purpose because it mirrors what the open-source client does
     *
     * @author mokl
     */
    public static class LookupResponse extends VaultResponse {
        private final long ttl;
        private final List<String> policies;
        private final String id;
        private final Map<String, String> meta;

        /**
         * - converts a RestResponse into a LookUp response (same way that the Java Vault Client does it)
         * - can be easily replaced if the code is added to the open source client at some point
         *
         * @param restResponse
         * @param retries
         */
        @SneakyThrows(ComputeException.class)
        public LookupResponse(final RestResponse restResponse, final int retries) {
            super(restResponse, retries);
            try {

                //
                // - attempt to extract some useful information out of the lookup body
                //
                final String responseJson = new String(restResponse.getBody(), StandardCharsets.UTF_8);
                final JsonObject jsonObject = Json.parse(responseJson).asObject();
                final JsonObject data = jsonObject.get("data").asObject();

                this.ttl = data.getLong("ttl", 0);
                this.id = data.getString("id", "");
                this.policies = new ArrayList<>();
                this.meta = new HashMap<>();

                if (!data.get(POLICIES).isNull()) {
                    for (final JsonValue policy : data.get(POLICIES).asArray()) {
                        this.policies.add(policy.asString());
                    }
                }
                if (!data.get("meta").isNull()) {
                    for (final JsonObject.Member policy : data.get("meta").asObject()) {
                        meta.put(policy.getName(), policy.getValue().asString());
                    }
                }
            } catch (final UnsupportedOperationException e) {
                throw ComputeException.builder()
                        .code(ComputeErrorCodes.VAULT_ERROR)
                        .message("Failed to convert REST response to LookUp response due to error=" + e.toString())
                        .cause(e)
                        .build();
            }
        }

        public long getTtl() {
            return ttl;
        }

    }

    /**
     * - customised vault functionality for Cloudos usage mirroring Auth, Leases, etc of the
     * java vault package
     * - provides functionality to execute a vault lookup on a token
     *
     * @author mokl
     */
    static class Customized {

        private final VaultConfig vcfg;

        Customized(final VaultConfig vcfg) {
            this.vcfg = vcfg;
        }

        /**
         * - the Vault driver already contains the token, URL, and configurations
         * - this simply executes a lookup against it
         *
         * @return
         * @throws VaultException
         */
        public VaultBase.LookupResponse lookupSelf() throws VaultException {
            int retryCount = 0;
            while (true) {
                try {
                    //
                    // - taken from vault-java-driver's renewSelf flow and modified for lookups
                    // - lookups are simple; we just want to see if there is a 200
                    //
                    final RestResponse resp = new Rest()
                            .url(vcfg.getAddress() + "/v1/auth/token/lookup-self")
                            .header(X_VAULT_TOKEN, vcfg.getToken())
                            .connectTimeoutSeconds(vcfg.getOpenTimeout())
                            .readTimeoutSeconds(vcfg.getReadTimeout())
                            .sslContext(SSLContext.getInstance(TLS_1_2))
                            .sslVerification(vcfg.getSslConfig().isVerify())
                            .get();
                    if (resp.getStatus() != 200) {
                        throw new VaultException(String.format(VAULT_ERROR_MESSAGE,
                                resp.getStatus(),
                                new String(resp.getBody(), StandardCharsets.UTF_8)),
                                resp.getStatus());
                    }
                    return new VaultBase.LookupResponse(resp, retryCount);
                } catch (final VaultException | RestException | NoSuchAlgorithmException e) {
                    // If there are retries to perform, then pause for the configured interval and then execute the loop again...
                    if (sleepBeforeRetry(retryCount, vcfg.getMaxRetries(), vcfg.getRetryIntervalMilliseconds())) {
                        retryCount++;
                    } else {
                        wrapAndRethrowAsVaultException(e);
                    }
                }
            }
        }
    }

    /**
     * - modified Auth class to remove the MIME type assert
     * - previously would explode on a Vault exception not with the exception, but with a mimetype assert text != json
     *
     * @author mokl
     */
    static class CloudosAuth extends Auth {

        private final VaultConfig vcfg;

        CloudosAuth(final VaultConfig vcfg) {
            super(vcfg);
            this.vcfg = vcfg;
        }

        /**
         * - helper to convert from the format expected by vault to a universal ttl in s
         * - ttls are expected to be durations
         *
         * @param ttlString
         * @return
         */
        private static long ttlConverter(final String ttlString) {
            final String ttl = ttlString.toLowerCase(Locale.ROOT);
            for (int i = 0; i < ttl.length(); i++) {
                if (Character.isLetter(ttl.charAt(i))) {
                    switch (ttl.charAt(i)) {
                        case 'h':
                            return Long.parseLong(ttl.substring(0, i)) * 3600L;
                        case 'm':
                            return Long.parseLong(ttl.substring(0, i)) * 60L;
                        default:
                            return Long.parseLong(ttl.substring(0, i));
                    }
                }
            }
            return Long.parseLong(ttl);
        }

        /**
         * - have the original function use our modified method instead
         */
        @Override
        public AuthResponse createToken(final TokenRequest request) throws VaultException {
            final VaultToken.VaultTokenBuilder builder = VaultToken.builder();
            if (request.getId() != null)
                builder.id(request.getId().toString());
            if (request.getPolices() != null)
                builder.policies(request.getPolices());
            if (request.getMeta() != null)
                builder.meta(request.getMeta());
            if (request.getNoDefaultPolicy() != null)
                builder.noDefaultPolicy(request.getNoDefaultPolicy());
            if (request.getNumUses() != null)
                builder.numUses(request.getNumUses());
            if (request.getDisplayName() != null)
                builder.displayName(request.getDisplayName());
            if (request.getTtl() != null)
                builder.ttl(ttlConverter(request.getTtl()));
            if (request.getNoParent() != null)
                builder.noParent(request.getNoParent());
            return createToken(builder.build());
        }

        /**
         * - modified copy of the origin vault-java-driver createToken method with the mimetype assertion removed
         * - takes a Token object instead now to create a derivative token
         *
         * @param token
         * @return
         * @throws VaultException
         */
        public AuthResponse createToken(final VaultToken token) throws VaultException {
            final AtomicInteger retryCount = new AtomicInteger(0);
            while (true) {
                try {
                    final UUID id = (token.getId() == null) ? null : UUID.fromString(token.getId());
                    final List<String> policies = token.getPolicies();
                    final Map<String, String> meta = token.getMeta();
                    final Boolean noParent = token.getNoParent();
                    final Boolean noDefaultPolicy = token.getNoDefaultPolicy();
                    final Long ttl = token.getTtl();
                    final String displayName = token.getDisplayName();
                    final Long numUses = token.getNumUses();

                    // Parse parameters to JSON
                    final JsonObject jsonObject = Json.object();
                    if (id != null) {
                        jsonObject.add("id", id.toString());
                    }
                    if (policies != null && !policies.isEmpty()) {
                        jsonObject.add(POLICIES, Json.array(policies.toArray(new String[policies.size()])));//NOPMD
                    }
                    if (meta != null && !meta.isEmpty()) {
                        final JsonObject metaMap = Json.object();
                        for (final Map.Entry<String, String> entry : meta.entrySet()) {
                            metaMap.add(entry.getKey(), entry.getValue());
                        }
                        jsonObject.add("meta", metaMap);
                    }
                    if (noParent != null) {
                        jsonObject.add("no_parent", noParent);
                    }
                    if (noDefaultPolicy != null) {
                        jsonObject.add("no_default_policy", noDefaultPolicy);
                    }
                    if (ttl != null) {
                        jsonObject.add("ttl", 1000);
                    }
                    if (displayName != null) {
                        jsonObject.add("display_name", displayName);
                    }
                    if (numUses != null) {
                        jsonObject.add("num_uses", numUses);
                    }
                    final String requestJson = jsonObject.toString();

                    // HTTP request to Vault
                    final RestResponse resp = new Rest()//NOPMD
                            .url(vcfg.getAddress() + "/v1/auth/token/create")
                            .header(X_VAULT_TOKEN, vcfg.getToken())
                            .body(requestJson.getBytes(StandardCharsets.UTF_8))
                            .connectTimeoutSeconds(vcfg.getOpenTimeout())
                            .readTimeoutSeconds(vcfg.getReadTimeout())
                            .sslContext(SSLContext.getInstance(TLS_1_2))
                            .sslVerification(vcfg.getSslConfig().isVerify())
                            .post();

                    // Validate restResponse
                    if (resp.getStatus() != 200) {
                        throw new VaultException(String.format(VAULT_ERROR_MESSAGE,
                                resp.getStatus(),
                                new String(resp.getBody(), StandardCharsets.UTF_8)),
                                resp.getStatus());
                    }
                    return new AuthResponse(resp, retryCount.get());
                } catch (final Exception e) {
                    retryIfPossible(retryCount, vcfg, e);
                }
            }
        }

        /**
         * - modified version of vault-java-driver renewSelf method with the mimetype assertion removed
         * - also added better debugging
         *
         * @param increment
         * @return
         * @throws VaultException
         */
        @Override
        public AuthResponse renewSelf(final long increment) throws VaultException {
            final AtomicInteger retryCount = new AtomicInteger(0);
            while (true) {
                try {
                    // HTTP request to Vault
                    final String requestJson = Json.object().add("increment", increment).toString();
                    final RestResponse resp = new Rest()//NOPMD
                            .url(this.vcfg.getAddress() + "/v1/auth/token/renew-self")
                            .header(X_VAULT_TOKEN, this.vcfg.getToken())
                            .body(increment < 0 ? null : requestJson.getBytes(StandardCharsets.UTF_8))
                            .connectTimeoutSeconds(this.vcfg.getOpenTimeout())
                            .readTimeoutSeconds(this.vcfg.getReadTimeout())
                            .sslContext(SSLContext.getInstance(TLS_1_2))
                            .sslVerification(this.vcfg.getSslConfig().isVerify())
                            .post();
                    // Validate restResponse
                    if (resp.getStatus() != 200) {
                        throw new VaultException(String.format(VAULT_ERROR_MESSAGE,
                                resp.getStatus(),
                                new String(resp.getBody(), StandardCharsets.UTF_8)),
                                resp.getStatus());
                    }
                    return new AuthResponse(resp, retryCount.get());
                } catch (final Exception e) {
                    retryIfPossible(retryCount, vcfg, e);
                }
            }
        }
    }

    /**
     * - modified Logical module, again with the mimetype assertions removed and a minimal amount of tuning
     *
     * @author mokl
     */
    static class CloudosLogical extends Logical {
        private final VaultConfig vcfg;

        CloudosLogical(final VaultConfig vcfg) {
            super(vcfg);
            this.vcfg = vcfg;
        }

        @Override
        public LogicalResponse read(final String path) throws VaultException {
            int retryCount = 0;
            while (true) {
                try {
                    // Make an HTTP request to Vault
                    final RestResponse resp = new Rest()//NOPMD
                            .url(vcfg.getAddress() + "/v1/" + path)
                            .header(X_VAULT_TOKEN, vcfg.getToken())
                            .connectTimeoutSeconds(vcfg.getOpenTimeout())
                            .readTimeoutSeconds(vcfg.getReadTimeout())
                            .sslContext(SSLContext.getInstance(TLS_1_2))
                            .sslVerification(vcfg.getSslConfig().isVerify())
                            .get();
                    // Validate response
                    if (resp.getStatus() != 200) {
                        throw new VaultException(String.format(VAULT_ERROR_MESSAGE,
                                resp.getStatus(),
                                new String(resp.getBody(), StandardCharsets.UTF_8)),
                                resp.getStatus());
                    }

                    return new LogicalResponse(resp, retryCount, logicalOperations.readV1);
                } catch (final RuntimeException | VaultException | RestException | NoSuchAlgorithmException e) {
                    // If there are retries to perform, then pause for the configured interval and then execute the loop again...
                    if (sleepBeforeRetry(retryCount, vcfg.getMaxRetries(), vcfg.getRetryIntervalMilliseconds())) {
                        retryCount++;
                    } else {
                        wrapAndRethrowAsVaultException(e);
                    }
                }
            }
        }

        @Override
        public LogicalResponse write(final String path, final Map<String, Object> nameValuePairs) throws VaultException {
            final AtomicInteger retryCount = new AtomicInteger(0);
            while (true) {
                try {
                    JsonObject requestJson = Json.object();
                    if (nameValuePairs != null) {
                        for (final Map.Entry<String, Object> pair : nameValuePairs.entrySet()) {
                            requestJson = requestJson.add(pair.getKey(), pair.getValue().toString());
                        }
                    }

                    final RestResponse resp = new Rest()//NOPMD
                            .url(vcfg.getAddress() + "/v1/" + path)
                            .body(requestJson.toString().getBytes(StandardCharsets.UTF_8))
                            .header(X_VAULT_TOKEN, vcfg.getToken())
                            .connectTimeoutSeconds(vcfg.getOpenTimeout())
                            .readTimeoutSeconds(vcfg.getReadTimeout())
                            .sslContext(SSLContext.getInstance(TLS_1_2))
                            .sslVerification(vcfg.getSslConfig().isVerify())
                            .post();

                    // HTTP Status should be either 200 (with content - e.g. PKI write) or 204 (no content)
                    final int restStatus = resp.getStatus();
                    if (restStatus == 200 || restStatus == 204) {
                        return new LogicalResponse(resp, retryCount.get(), logicalOperations.writeV1);
                    } else {
                        throw new VaultException(String.format(VAULT_ERROR_MESSAGE,
                                resp.getStatus(),
                                new String(resp.getBody(), StandardCharsets.UTF_8)),
                                resp.getStatus());
                    }
                } catch (final Exception e) {
                    retryIfPossible(retryCount, vcfg, e);
                }
            }
        }

    }

    /**
     * - extended vault client with the Customized functionality from above
     *
     * @author mokl
     */
    static class CloudosVault extends Vault {
        protected final VaultConfig vcfg;

        public CloudosVault(final VaultConfig vcfg, final int retries, final int cooldown) {
            super(vcfg);
            this.withRetries(retries, cooldown);
            this.vcfg = vcfg;
        }

        public VaultBase.Customized customized() {
            return new VaultBase.Customized(vcfg);
        }

        @Override
        public Auth auth() {
            return new VaultBase.CloudosAuth(vcfg);
        }

        @Override
        public Logical logical() {
            return new VaultBase.CloudosLogical(vcfg);
        }

        public VaultBase.CloudosAuth cloudosAuth() {
            return new VaultBase.CloudosAuth(vcfg);
        }
    }

}
