package com.autodesk.compute.auth;

import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.common.SSMUtils;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.VersionedSecret;
import jakarta.inject.Inject;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class WorkerCredentials {
    private final SSMUtils ssmUtils;
    private final ConcurrentHashMap<String, List<VersionedSecret>> credentials;
    private long lastLoadedTime;
    private final String baseCredentialsPath;

    // Reload worker credentials every 2 hours
    private static final int RELOAD_SCHEDULE_DURATION_IN_MINUTES = 120;

    // https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
    private static class LazyWorkerCredentialsHolder {  //NOPMD
        public static final WorkerCredentials INSTANCE = new WorkerCredentials();
    }
    public static WorkerCredentials getInstance() {
        return LazyWorkerCredentialsHolder.INSTANCE;
    }


    @Inject
    private WorkerCredentials() {
        this(SSMUtils.of());
    }

    public WorkerCredentials(final SSMUtils utils) {
        this.ssmUtils = utils;
        credentials = new ConcurrentHashMap<>();
        lastLoadedTime = 0;
        baseCredentialsPath = "/ComputeWorkerCredentials";    // NOSONAR - not a URL or file path
        tryLoadCredentials();
    }

    // TEST-ONLY (for now): this resets the loaded time so the secrets get reloaded the next time
    // a credential is validated
    public void forceReload() {
        lastLoadedTime = 0;
    }

    // This is paranoia only. If we got a throttling exception in the constructor, try again later.
    public void tryLoadCredentials() {
        if (lastLoadedTime > 0 && (Instant.now().getEpochSecond() - lastLoadedTime) < RELOAD_SCHEDULE_DURATION_IN_MINUTES)
            return;
        try {
            loadCredentials();
            lastLoadedTime = Instant.now().getEpochSecond();
        } catch (final ComputeException e) {
            log.error("Failed to load credentials; will try again later...", e);
        }
    }

    private void loadCredentials() throws ComputeException {
        log.info("Loading worker credentials....");
        final Map<String, VersionedSecret> currentSecrets = ssmUtils.getSecretsByPathAndLabel(
                baseCredentialsPath, SSMUtils.SecretType.CURRENT.toString());
        final Map<String, VersionedSecret> fallbackSecrets = ssmUtils.getSecretsByPathAndLabel(
                baseCredentialsPath, SSMUtils.SecretType.FALLBACK.toString());

        final HashMap<String, List<VersionedSecret>> mergedSecrets = new HashMap<>();
        for (final Map.Entry<String, VersionedSecret> entry : currentSecrets.entrySet()) {
            final ArrayList<VersionedSecret> mergedValues = new ArrayList<>();
            mergedValues.add(entry.getValue());
            if (fallbackSecrets.containsKey(entry.getKey())) {
                mergedValues.add(fallbackSecrets.get(entry.getKey()));
            }
            mergedSecrets.put(entry.getKey(), mergedValues);
        }

        credentials.putAll(mergedSecrets);
        lastLoadedTime = Instant.now().getEpochSecond();
        log.info("Loading worker credentials....Done");
    }

    public String credentialKey(@NonNull final String id) {
        return baseCredentialsPath + "/" + id;
    }

    public Optional<String> getCurrentSecret(final String id) {
        tryLoadCredentials();
        final List<VersionedSecret> secrets = credentials.getOrDefault(credentialKey(id), Collections.emptyList());
        if (secrets.isEmpty())
            return Optional.empty();
        return Optional.of(secrets.get(0).getSecret());
    }

    public boolean isAuthorized(final String id, final String password) {
        final Optional<VersionedSecret> foundSecret;
        tryLoadCredentials();
        final List<VersionedSecret> secrets = credentials.getOrDefault(credentialKey(id), Collections.emptyList());
        foundSecret = secrets.stream().filter(
                oneSecret -> oneSecret.getSecret().compareTo(password) == 0).findFirst();
        foundSecret.ifPresent(
                secret -> {
                    Metrics.getInstance().reportWorkerAuthSucceeded(id);
                    if (secret.getLabel().equals(SSMUtils.SecretType.FALLBACK.toString())) {
                        Metrics.getInstance().reportFallbackAuthUsed(id);
                    }
                }
        );
        if (foundSecret.isEmpty()) {
            Metrics.getInstance().reportWorkerAuthFailed(id);
        }
        return foundSecret.isPresent();
    }

}
