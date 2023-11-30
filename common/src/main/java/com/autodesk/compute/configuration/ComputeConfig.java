package com.autodesk.compute.configuration;

import com.amazonaws.util.StringUtils;
import com.autodesk.compute.auth.oauth.OxygenAuthRequestFilter;
import com.autodesk.compute.auth.vault.VaultAuthDriver;
import com.autodesk.compute.common.ComputeBatchDriver;
import com.autodesk.compute.common.SSMUtils;
import com.autodesk.compute.dynamodb.IDynamoDBJobClientConfig;
import com.google.common.base.Splitter;
import io.lettuce.core.RedisURI;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.spi.ResteasyProviderFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.autodesk.compute.common.ComputeStringOps.isNullOrEmpty;
import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.configuration.ComputeConstants.DynamoFields.*;
import static java.util.stream.Collectors.toList;

@Getter
@Slf4j
public class ComputeConfig implements IDynamoDBJobClientConfig {
    protected ConfigurationReader configurationReader;

    private final String awsAccountId;
    private final String appMoniker;

    private final boolean isFedRampEnvironment;
    private final String region;
    private final String cloudosMoniker;

    private final String dbProgressTable;
    private final String dbProgressTableIndex = PROGRESS_TABLE_INDEX;
    private final String dbProgressTableSearchIndex = PROGRESS_TABLE_SEARCH_INDEX;
    private final String dbProgressTableQueueingIndex = PROGRESS_TABLE_QUEUEING_INDEX;
    private final String dbProgressTableArrayJobIndex = PROGRESS_TABLE_ARRAY_JOB_INDEX;
    
    private final String dbSnsTable;
    private final String dbGatekeeperStateTable;

    private final Integer batchQueueRedundancy;
    private final Map<String, String> batchQueue;
    private final Map<String, String> batchGPUQueue;
    private final ComputeBatchDriver.ForceTerminationFeature batchTerminationOnCancelJob;
    private final String revision;
    private final String workerManagerName;
    private final String workerManagerURL;
    private final String testWorkerManagerURL;

    private final String oxygenUrl;
    private final String oxygenClientId;
    private final String oxygenClientSecret;
    private final Set<String> apigeeSecrets;


    private final Integer cacheExpireAfterAccessSeconds;
    private final Integer cacheRefreshAfterWriteSeconds;
    private final String portfolioVersion;
    private final VaultAuthDriver.Configuration vaultAuthDriverConfiguration;
    private final OxygenAuthRequestFilter.Configuration oxygenReqFilterConfiguration;
    private final boolean enforceWorkerAuthentication;
    private final RedisURI redisUri;  // eg redis://fpccomp-c-uw2-sb-erg.givcm9.ng.0001.usw2.cache.amazonaws.com:6379
    private final String redisToken;

    private final String terminalJobsSqsUrl;
    private final String secretsParametersPath;
    private final boolean useRedisNamespace;

    // Puts job in Database and returns HTTP 202 and creates job asynchronously.
    private final boolean createJobAsynchronously;
    // List of monikers to be excluded form async job creation if createJobAsynchronously is true
    private final List<String> createJobAsynchronousExclusions;
    private final List<Pattern> createJobAsynchronousExclusionsPatterns;

    private final String statsdURI;
    private final Integer statsdPort;

    // https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
    private static class LazyComputeConfigHolder {  //NOPMD
        public static final ComputeConfig INSTANCE = new ComputeConfig("conf");
    }

    public static ComputeConfig getInstance() {
        return LazyComputeConfigHolder.INSTANCE;
    }

    @SneakyThrows({ComputeException.class})
    protected ComputeConfig(final String conf) { //NOPMD
        configurationReader = new ConfigurationReader(conf);
        this.secretsParametersPath = configurationReader.readString(ConfigurationKeys.SECRETS_PARAMETERS_PATH);

        if (Files.exists(Paths.get(ComputeConstants.GK.REVISION), LinkOption.NOFOLLOW_LINKS)) {
            try {
                this.revision = Files.readAllLines(Paths.get(ComputeConstants.GK.REVISION)).
                        stream().collect(Collectors.joining());
            } catch (final IOException e) {
                throw ComputeException.builder()
                        .code(ComputeErrorCodes.CONFIGURATION)
                        .message("Failed to read configuration due to error=" + e.toString())
                        .cause(e)
                        .build();
            }
        } else {
            this.revision = ComputeConstants.GK.LOCAL;
        }

        this.portfolioVersion = configurationReader.readString("cos.portfolio.version", "deployed");
        this.awsAccountId = configurationReader.readString("aws.account.id");

        this.dbProgressTable = configurationReader.readString("dynamodb.table.name");

        this.cloudosMoniker = configurationReader.readString(ConfigurationKeys.CLOUDOS_MONIKER,
            configurationReader.readString(ConfigurationKeys.COS_MONIKER, ""));
        this.region = configurationReader.readString(ConfigurationKeys.REGION);
        this.dbSnsTable = configurationReader.readString("sns.table.name", "");
        this.cacheExpireAfterAccessSeconds = configurationReader.readInt("cache.expiry.seconds", 300);
        this.cacheRefreshAfterWriteSeconds = configurationReader.readInt("cache.refresh.seconds", 10);

        String queueList = configurationReader.readString("batch.queue.name");
        this.batchQueue = Splitter.on(",").withKeyValueSeparator(":").split(queueList);
        queueList = configurationReader.readString("batch.gpu.queue.name");
        this.batchGPUQueue = Splitter.on(",").withKeyValueSeparator(":").split(queueList);
        this.batchTerminationOnCancelJob = configurationReader.readBoolean("batch.terminate.on.cancel", Boolean.FALSE) ? ComputeBatchDriver.ForceTerminationFeature.ENABLED : ComputeBatchDriver.ForceTerminationFeature.DISABLED;
        this.workerManagerName = configurationReader.readString("worker.manager.name", "worker-manager");
        this.appMoniker = configurationReader.readString("app.moniker");
        this.isFedRampEnvironment = appMoniker.toLowerCase().contains("-sfr-") || appMoniker.toLowerCase().contains("-pfr-");
        this.dbGatekeeperStateTable = configurationReader.readString("dynamodb.gatekeeper.state.table.name");
        this.enforceWorkerAuthentication = configurationReader.readBoolean("worker.authentication.enforced", Boolean.FALSE);
        this.useRedisNamespace = configurationReader.readBoolean("redis.namespace.enabled", Boolean.TRUE);

        // Secrets
        final String cosSecretsManagerPath = System.getenv("COS_SECRETS_MANAGER_PATH");
        final String secretsPath = cosSecretsManagerPath != null ? cosSecretsManagerPath : "/" + appMoniker;
        final Map<String, String> paramStoreSecrets = SSMUtils.of().getSecretsByPath(secretsPath);
        this.oxygenUrl = paramStoreSecrets.get(getSecretPath(secretsPath, "OXYGEN_URL"));
        this.oxygenClientId = paramStoreSecrets.get(getSecretPath(secretsPath, "OXYGEN_CLIENT_ID"));
        this.oxygenClientSecret = paramStoreSecrets.get(getSecretPath(secretsPath, "OXYGEN_CLIENT_SECRET"));

        // APIGEE_SECRET contains space separated list of apigee gateway secrets.
        // This is recompounded by Apigee team to support secret rotation in which case Apigee may pass older secret
        // during rotation, and we may want to check against both old and rotated secrets.
        final String secrets = paramStoreSecrets.get(getSecretPath(secretsPath, "APIGEE_SECRET"));
        apigeeSecrets = Splitter.on(" ").splitToStream(secrets).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

        this.redisToken = paramStoreSecrets.getOrDefault(getSecretPath(secretsPath, "REDIS_AUTH_TOKEN"), "");
        final String redisUriWithoutToken = configurationReader.readString("redis.uri", "");
        this.redisUri = makeRedisUri(redisUriWithoutToken, redisToken);

        // SQS queue for terminal jobs notification
        this.terminalJobsSqsUrl = makeString("https://sqs.", this.region, ".amazonaws.com/", this.awsAccountId, "/", this.appMoniker.toLowerCase(), "_terminal_jobs_queue.fifo");

        this.createJobAsynchronously = configurationReader.readBoolean("job.create.asynchronous", Boolean.FALSE);
        final String excludedMonikers = configurationReader.readString("job.create.asynchronousExclusions", "");
        this.createJobAsynchronousExclusions = Arrays.stream(excludedMonikers.split(","))
                .map(String::trim).filter(s -> !StringUtils.isNullOrEmpty(s)).collect(toList());

        this.createJobAsynchronousExclusionsPatterns = createJobAsynchronousExclusions.stream()
                .map(regex -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE)).collect(toList());

        //example WORKER_MANAGER_URL=https://WORKER_MANAGER_NAME.APP_MONIKER.COS_MONIKER.autodesk.com
        // todo: handle case where we want the jobs to reach out to wmgreen worker manager
        this.workerManagerURL = makeString( "https://", workerManagerName,  ".", appMoniker, ".",
                (isCosV3() ? "cloudos" : getCloudosMoniker()),  ".autodesk.com");

        this.testWorkerManagerURL = makeString( "https://", workerManagerName,  ".", appMoniker, "-test.",
                (isCosV3() ? "cloudos" : getCloudosMoniker()),  ".autodesk.com");
        
        vaultAuthDriverConfiguration = new VaultAuthDriver.Configuration(VaultConfiguration.builder()
                .vaultAddr(configurationReader.readString(ConfigurationKeys.VAULT_VAULT_ADDR))
                .vaultCacheExpiryTime(configurationReader.readLong(ConfigurationKeys.VAULT_CACHE_EXPIRY_TIME))
                .vaultCacheExpiryUnit(TimeUnit.valueOf(configurationReader.readString(ConfigurationKeys.VAULT_CACHE_EXPIRY_UNIT)))
                .vaultCacheSize(configurationReader.readLong(ConfigurationKeys.VAULT_CACHE_SIZE))
                .vaultOpCooldown(configurationReader.readInt(ConfigurationKeys.VAULT_OP_COOLDOWN))
                .vaultOpRetries(configurationReader.readInt(ConfigurationKeys.VAULT_OP_RETRIES))
                .cloudosMoniker(getCloudosMoniker())
                .build());

        oxygenReqFilterConfiguration = new O2AuthRequestFilterImpl(apigeeSecrets,
                configurationReader.readBoolean(ConfigurationKeys.APIGEE_ENFORCE, Boolean.FALSE)
        );

        statsdURI = configurationReader.readString("statsd.uri", null);
        statsdPort = configurationReader.readInt("statsd.port", 8125);
        batchQueueRedundancy = configurationReader.readInt("batch.queue.redundancy", 5);
    }

    private RedisURI makeRedisUri(final String redisUriWithoutToken, final String redisAuthToken) {
        URI redisUri = URI.create(redisUriWithoutToken);
        if (isNullOrEmpty(redisAuthToken) && redisUri.getScheme().equals("rediss")) {
            throw new IllegalArgumentException("Secure Redis URI specified, but no redis auth token was specified.");
        }
        if (redisUri.getAuthority().contains("@"))
            throw new IllegalArgumentException("Do not put a Redis Auth token in the Redis URI. Put it in the REDIS_AUTH_TOKEN secret instead.");

        boolean useSSL = redisUri.getScheme().equals("rediss");
        if (!useSSL && !isNullOrEmpty(redisAuthToken))
            log.warn("Redis auth token specified, but the Redis URI is not secure. Ignoring the auth token.");

        var builder =  RedisURI.builder()
                .withHost(redisUri.getHost())
                .withPort(redisUri.getPort())
                .withSsl(useSSL);
        if (useSSL && !isNullOrEmpty(redisAuthToken))
            builder.withPassword(redisAuthToken);
        return builder.build();
    }

    protected String getSecretPath(final String appMoniker, final String keyName) {
        return makeString(appMoniker, "/", keyName);
    }

    @AllArgsConstructor
    @Getter
    private static class O2AuthRequestFilterImpl implements OxygenAuthRequestFilter.Configuration {
        private final Set<String> apigeeSecrets;
        private final boolean enforceApigee;

        @Override
        public boolean enforceApigee() {
            return enforceApigee;
        }
    }

    private static String getRequestServerName() {
        final HttpServletRequest contextData = ResteasyProviderFactory.getInstance().getContextData(HttpServletRequest.class);
        return contextData != null ? contextData.getServerName() : null;
    }

    public static boolean isTestRequest() {
        return isTestServer(getRequestServerName());
    }

    public static boolean isTestServer(final String url) {
        if (StringUtils.isNullOrEmpty(url))
            return false;

        return url.contains("-test");
    }

    public static boolean isCosV3() {
        return System.getenv("COS_SECRETS_MANAGER_PATH") != null;
    }

    @Override
    public String getIdempotentJobsTable() {
        return dbProgressTable.replace("job_progress_data", "idempotent_jobs");
    }

    @Override
    public String getActiveJobsCountTable() {
        return dbProgressTable.replace("job_progress_data", "active_jobs_count");
    }

    public boolean createJobsAsynchronously(final String service) {
        return isCreateJobAsynchronously() && !isCreateJobAsynchronousExclusion(service);
    }

    private boolean isCreateJobAsynchronousExclusion(final String service) {
        return createJobAsynchronousExclusionsPatterns.stream().anyMatch(p -> p.matcher(service).matches());
    }
}
