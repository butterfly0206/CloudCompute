package com.autodesk.compute.testlambda;

import com.amazonaws.util.StringUtils;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.SSMUtils;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.configuration.ConfigurationKeys;
import com.autodesk.compute.configuration.ConfigurationReader;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Map;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Getter
@Slf4j
public class Config {

    private static final String COMPUTE_JOB_HEALTH_CHECK_NAME = "compute_jobs";
    public static final String JOB_HEALTH_LOG_FILE = COMPUTE_JOB_HEALTH_CHECK_NAME + "_health.log";

    private static final String OXYGEN_CLIENT_ID = "OXYGEN_CLIENT_ID";
    private static final String OXYGEN_CLIENT_SECRET = "OXYGEN_CLIENT_SECRET";
    private static final String OXYGEN_URL = "OXYGEN_URL";
    private static final String SLACK_CRITICAL_WEBHOOK_URL = "SLACK_CRITICAL_WEBHOOK_URL";
    private static final String COS_PORTFOLIO_VERSION = "COS_PORTFOLIO_VERSION";

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd_HHmmss");

    // 3 minutes of jobs
    public static final long DEFAULT_JOB_TIMEOUT_SECONDS = 60L * 3;

    // five minutes of jobs, in seconds
    public static final long SEARCH_DURATION_SECONDS = 60L * 5;

    private static final int DEFAULT_JOBS_COUNT = 1;

    protected ConfigurationReader configurationReader;

    private final String cloudosMoniker;
    private final String cloudosPortfolioVersion;
    private final String appMoniker;
    private final String batchWorker;
    private final String ecsWorker;
    private final String gpuWorker;
    private final long batchJobTimeoutSeconds;
    private final long ecsJobTimeoutSeconds;
    private final int ecsJobsCount;
    private final int batchJobsCount;
    private final int gpuJobsCount;

    private final String slackCriticalWebhookUrl;
    private final String jobManagerUrl;
    private final String oxygenUrl;
    private final String oxygenClientId;
    private final String oxygenClientSecret;
    private final String sqsEndpointUrl;
    private final String region;
    private final String account;

    private final String healthBucket;

    // https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
    private static class LazyConfigHolder { //NOPMD
        public static final Config INSTANCE = new Config("conf");
    }

    private static String cosv3ApigeeProxyName(String appMoniker) {
        return appMoniker.toLowerCase().endsWith("ew1") ? "fpccomp-ew1" : "fpccomp";
    }
    public static Config getInstance() {
        return LazyConfigHolder.INSTANCE;
    }

    @SneakyThrows(ComputeException.class)
    protected Config(final String conf) {
        configurationReader = new ConfigurationReader(conf);
        this.cloudosMoniker = configurationReader.readString(ConfigurationKeys.COS_MONIKER, "");
        this.cloudosPortfolioVersion = configurationReader.readString(COS_PORTFOLIO_VERSION);
        this.appMoniker = configurationReader.readString("app.moniker");
        this.batchWorker = configurationReader.readString("app.batchWorker", "");
        this.ecsWorker = configurationReader.readString("app.ecsWorker", "");
        this.gpuWorker = configurationReader.readString("app.gpuWorker", "");

        // Timeouts should not be more then 14 minutes as Lambda can run maximum of 15 minutes.
        this.batchJobTimeoutSeconds = Math.min(840, configurationReader.readLong("app.batch.job.timeout.seconds", DEFAULT_JOB_TIMEOUT_SECONDS));
        this.ecsJobTimeoutSeconds = Math.min(840, configurationReader.readLong("app.ecs.job.timeout.seconds", DEFAULT_JOB_TIMEOUT_SECONDS));

        // No worker configured = 0 jobs
        this.ecsJobsCount = !ComputeStringOps.isNullOrEmpty(this.ecsWorker) ? configurationReader.readInt("app.ecs.jobs.count", DEFAULT_JOBS_COUNT) : 0;
        this.batchJobsCount = !ComputeStringOps.isNullOrEmpty(this.batchWorker) ? configurationReader.readInt("app.batch.jobs.count", DEFAULT_JOBS_COUNT) : 0;
        this.gpuJobsCount = !ComputeStringOps.isNullOrEmpty(this.gpuWorker) ? configurationReader.readInt("app.gpu.jobs.count", DEFAULT_JOBS_COUNT) : 0;

        // Secrets
        final String cosSecretsManagerPath = System.getenv("COS_SECRETS_MANAGER_PATH");
        final String secretsPath = StringUtils.isNullOrEmpty(cosSecretsManagerPath)
                ? makeString("/", appMoniker) : cosSecretsManagerPath;
        final Map<String, String> paramStoreSecrets = SSMUtils.of().getSecretsByPath(secretsPath);

        this.slackCriticalWebhookUrl = paramStoreSecrets.get(getSecretPath(secretsPath, SLACK_CRITICAL_WEBHOOK_URL));
        this.oxygenUrl = paramStoreSecrets.get(getSecretPath(secretsPath, OXYGEN_URL));
        this.oxygenClientId = paramStoreSecrets.get(getSecretPath(secretsPath, OXYGEN_CLIENT_ID));
        this.oxygenClientSecret = paramStoreSecrets.get(getSecretPath(secretsPath, OXYGEN_CLIENT_SECRET));

        final String apigeeHost = configurationReader.readString("apigee.host");
        // Cosv3 is using new proxies name 'fpccomp'
        final String apigeeProxyName = ComputeConfig.isCosV3() ? cosv3ApigeeProxyName(appMoniker) : appMoniker;
        this.jobManagerUrl = configurationReader.readString("jobmanager.test.url",
                makeString("https://", apigeeHost, "/", apigeeProxyName,  "/jm/api/v1"));
        this.region = configurationReader.readString(ConfigurationKeys.REGION);
        this.account = configurationReader.readString("AWS_ACCOUNT_ID");
        this.sqsEndpointUrl = makeString("https://sqs.", this.region, ".amazonaws.com/", this.account, "/", this.appMoniker, "-test-job-completion-queue");

        healthBucket = makeString(ComputeConfig.isCosV3() ? appMoniker.toLowerCase() : cloudosMoniker.toLowerCase(), "-health");

    }

    public String getHistoryLogFileName() {
        return makeString("history/", COMPUTE_JOB_HEALTH_CHECK_NAME, "/",
                COMPUTE_JOB_HEALTH_CHECK_NAME, "_health_", dateTimeFormatter.print(DateTime.now()), ".log");
    }

    protected String getSecretPath(final String secretsPath, final String keyName) {
        return makeString(secretsPath, "/", keyName);
    }

    public long getBatchJobTimeoutSeconds() {
        return batchJobTimeoutSeconds;
    }

    public long getEcsJobTimeoutSeconds() {
        return ecsJobTimeoutSeconds;
    }
}
