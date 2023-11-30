package com.autodesk.compute.common;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.*;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.*;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.VersionedSecret;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import jakarta.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.common.ComputeStringOps.makeString;

@AllArgsConstructor
@Slf4j
public class SSMUtils {
    // Labels to use for password rotations
    @AllArgsConstructor
    public enum SecretType {
        CURRENT("Current", "AWSCURRENT"),
        FALLBACK("Fallback", "AWSPREVIOUS");

        private final String name;
        private final String secretsManagerName;

        @Override
        public String toString() {
            return name;
        }
    }

    private final AWSSimpleSystemsManagement ssmClient;
    private final AWSSecretsManager smClient;

    public static SSMUtils of() {
        return SSMUtils.of(null, null);
    }

    public static SSMUtils of(final String region) {
        return SSMUtils.of(null, region);
    }

    public static SSMUtils of(final AWSCredentials credentials, final String region) {
        final String cosSecretsManagerPath = SSMUtils.getCosSecretsManagerPath();
        if (cosSecretsManagerPath != null) {
            return new SSMUtils(null, makeStandardClient(AWSSecretsManagerClientBuilder.standard(), credentials, region));
        }
        return new SSMUtils(makeStandardClient(AWSSimpleSystemsManagementClientBuilder.standard(), credentials, region), null);
    }

    private static String getCosSecretsManagerPath() {
        return System.getenv("COS_SECRETS_MANAGER_PATH");
    }

    @Inject
    public SSMUtils() {
        this(makeStandardClient(AWSSimpleSystemsManagementClientBuilder.standard()), null);
    }

    // For v3 secrets, the path has to contain COS_SECRETS_MANAGER_PATH
    public String getSecretByPath(final String path) throws ComputeException {
        if (smClient != null) {
            if (!path.startsWith(SSMUtils.getCosSecretsManagerPath())) {
                throw new ComputeException(ComputeErrorCodes.CONFIGURATION, "Secret doesn't start with needed prefix");
            }

            final Map<String, String> secretsManagerSecrets = readSecretsManagerSecrets(SSMUtils.getCosSecretsManagerPath());
            final String secret = secretsManagerSecrets.get(path);
            if (secret == null) {
                throw new ComputeException(ComputeErrorCodes.CONFIGURATION, makeString("secretsManager secrets is null for secret path: ", path));
            }
            return secret;
        }

        try {
            final GetParameterResult result =
                    ssmClient.getParameter(new GetParameterRequest()
                            .withName(path)
                            .withWithDecryption(true));
            if (result == null) {
                throw new ComputeException(ComputeErrorCodes.CONFIGURATION, "GetParameterResult is null");
            }
            return result.getParameter().getValue();
        } catch (final AWSSimpleSystemsManagementException e) {
            log.error("SSMUtils.getSecretByPath failed for path " + path + ": ", e);
            throw new ComputeException(ComputeErrorCodes.CONFIGURATION, "getSecretByPath", e);
        }
    }

    public Map<String, VersionedSecret> getSecretsByPathAndLabel(@NonNull final String path, @NonNull final String label) throws ComputeException {
        String nextToken = null;
        final Map<String, VersionedSecret> parameters = new HashMap<>();  //NOPMD
        if (smClient != null) {
            // Backwards compatibility with v2 solution:
            // If the requested label is FALLBACK, use AWSPREVIOUS. Otherwise, use AWSCURRENT.
            final String secretVersion = SecretType.FALLBACK.name.equals(label) ? SecretType.FALLBACK.secretsManagerName : SecretType.CURRENT.secretsManagerName;
            do {
                final ListSecretsRequest request = new ListSecretsRequest()
                        .withFilters(Collections.singletonList(
                                new Filter().withKey("name").withValues(path)));
                final Pair<String, List<SecretListEntry>> nextPageOfSecrets = getPageOfSMSecrets(request, nextToken);
                nextToken = nextPageOfSecrets.getLeft();
                for (final SecretListEntry secretEntry : nextPageOfSecrets.getRight()) {
                    // If the secret has the target version, return it. Otherwise, ignore it
                    if (secretEntry.getSecretVersionsToStages()
                            .entrySet().stream()
                            .anyMatch(e -> e.getValue().contains(secretVersion))) {
                        parameters.put(secretEntry.getName(), new VersionedSecret(
                                secretEntry.getLastChangedDate().toInstant().getEpochSecond(),
                                secretVersion,
                                // ListSecretsRequest doesn't return the secret value; we must ask for it
                                readSecretsManagerSecret(secretEntry.getName(), secretVersion),
                                secretVersion));
                    }
                }
            } while (!ComputeStringOps.isNullOrEmpty(nextToken));
            return parameters;
        }

        do {
            final GetParametersByPathRequest request = new GetParametersByPathRequest()
                    .withPath(path)
                    .withParameterFilters(Collections.singletonList(
                            new ParameterStringFilter()
                                    .withKey("Label").withValues(label).withOption("Equals")))
                    .withWithDecryption(true);
            final Pair<String, List<Parameter>> nextPageOfSecrets = getPageOfSSMSecrets(request, nextToken);
            nextToken = nextPageOfSecrets.getLeft();
            for (final Parameter parameter : nextPageOfSecrets.getRight()) {
                parameters.put(parameter.getName(), new VersionedSecret(
                        parameter.getLastModifiedDate().toInstant().getEpochSecond(),
                        parameter.getVersion().toString(),
                        parameter.getValue(),
                        label));
            }
        } while (!ComputeStringOps.isNullOrEmpty(nextToken));
        return parameters;
    }

    private Pair<String, List<Parameter>> getPageOfSSMSecrets(final GetParametersByPathRequest argRequest, final String nextToken) throws ComputeException {
        GetParametersByPathRequest request = argRequest;
        if (!ComputeStringOps.isNullOrEmpty(nextToken))
            request = request.withNextToken(nextToken);
        final GetParametersByPathResult result = ssmClient.getParametersByPath(request);
        if (result == null) {
            throw new ComputeException(ComputeErrorCodes.CONFIGURATION, "ssmClient.getParametersByPath is null");
        }
        return Pair.of(result.getNextToken(), result.getParameters());
    }

    private Pair<String, List<SecretListEntry>> getPageOfSMSecrets(final ListSecretsRequest argRequest, final String nextToken) throws ComputeException {
        ListSecretsRequest request = argRequest;
        if (!ComputeStringOps.isNullOrEmpty(nextToken))
            request = request.withNextToken(nextToken);
        try {
            final ListSecretsResult result = smClient.listSecrets(request);
            if (result == null) {
                throw new ComputeException(ComputeErrorCodes.CONFIGURATION, "smClient.listSecrets is null");
            }
            return Pair.of(result.getNextToken(), result.getSecretList());
        } catch (final AWSSecretsManagerException e) {
            throw new ComputeException(ComputeErrorCodes.CONFIGURATION, "smClient.listSecrets has failed", e);
        }
    }

    public Map<String, String> getSecretsByPath(final String path) throws ComputeException {
        if (smClient != null) {
            return readSecretsManagerSecrets(path);
        }

        final Map<String, String> parameters = new HashMap<>();   //NOPMD
        String nextToken = null;
        do {
            final GetParametersByPathRequest request = new GetParametersByPathRequest()
                    .withPath(path)
                    .withWithDecryption(true);
            final Pair<String, List<Parameter>> nextPage = getPageOfSSMSecrets(request, nextToken);
            nextToken = nextPage.getLeft();

            nextPage.getRight().forEach(
                    parameter -> parameters.put(parameter.getName(), parameter.getValue()));
        } while (!ComputeStringOps.isNullOrEmpty(nextToken));
        return parameters;
    }


    private Map<String, String> readSecretsManagerSecrets(final String secretId) throws ComputeException {
        return readSecretsManagerSecrets(secretId, SecretType.CURRENT.secretsManagerName);
    }

    // Reads all json key/value secrets from the secretId
    private Map<String, String> readSecretsManagerSecrets(final String secretId, final String versionStage) throws ComputeException {
        try {
            final GetSecretValueResult result = smClient.getSecretValue(new GetSecretValueRequest()
                    .withSecretId(secretId)
                    .withVersionStage(versionStage));
            if (result == null) {
                log.warn("SSMUtils.readSecretsManagerSecrets failed for secret {}", secretId);
                throw new ComputeException(ComputeErrorCodes.CONFIGURATION, "GetSecretValueResult is null");
            }

            final String cosSecretsManagerPath = SSMUtils.getCosSecretsManagerPath();
            // Remember that each entry in secrets manager is a json key/value map
            final Map<String, String> jsonMap = Json.fromJsonStringToMap(result.getSecretString());
            return jsonMap.entrySet().stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(makeString(cosSecretsManagerPath, "/", e.getKey()), e.getValue()))
                    .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        } catch (final AWSSecretsManagerException e) {
            log.error("SSMUtils.readSecretsManagerSecrets failed for secret " + secretId + ": ", e);
            throw new ComputeException(ComputeErrorCodes.CONFIGURATION, "SSMUtils.readSecretsManagerSecrets failed", e);
        }
    }

    // Reads only the first key of the json of key/value secrets from the secretId
    // Use this when you are expecting the json to hold only one key
    private String readSecretsManagerSecret(final String secretId) throws ComputeException {
        return readSecretsManagerSecret(secretId, SecretType.CURRENT.secretsManagerName);
    }

    // Reads only the first key of the json of key/value secrets from the secretId
    // Use this when you are expecting the json to hold only one key
    private String readSecretsManagerSecret(final String secretId, final String versionStage) throws ComputeException {
        return readSecretsManagerSecrets(secretId, versionStage)
                .values().stream()
                .findFirst()
                .orElseThrow(() -> new ComputeException(ComputeErrorCodes.CONFIGURATION, "SSMUtils.readSecretsManagerSecret did not find anything"));
    }
}
