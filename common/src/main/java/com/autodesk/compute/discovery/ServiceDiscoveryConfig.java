package com.autodesk.compute.discovery;

import com.autodesk.compute.common.SSMUtils;
import com.autodesk.compute.configuration.*;
import com.google.common.base.Splitter;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Getter
@Slf4j
public class ServiceDiscoveryConfig {

    public enum CloudOSVersion {
        COSV2(2), COSV3(3);
        private int value;

        CloudOSVersion(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

    }

    private final List<CloudOSVersion> discoveryOrders;

    private final String gatekeeperUrl;
    private final String sinkUrl;

    private final String awsAccessKey;
    private final String awsSecretKey;
    private final String awsRegion;

    private final String stateMachineRoleArn;

    private final String revision;

    private final String adfUpdateSNSTopicARN;
    private final String adfSubscriptionEndpoint;
    private final String vaultAddress;
    private final String vaultUsername;
    private final String vaultPassword;

    private final String appMoniker;
    private final String portfolioVersion;

    private final Map<CloudOSVersion, String> adfUrl;

    protected final ConfigurationReader configurationReader;

    // https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
    private static class LazyServiceDiscoveryConfigHolder { //NOPMD
        public static final ServiceDiscoveryConfig INSTANCE = new ServiceDiscoveryConfig("conf");
    }

    public static ServiceDiscoveryConfig getInstance() {
        return LazyServiceDiscoveryConfigHolder.INSTANCE;
    }

    protected String readSecret(final String secretsPath) throws ComputeException {
        return SSMUtils.of().getSecretByPath(secretsPath);
    }

    @SneakyThrows(ComputeException.class)
    private ServiceDiscoveryConfig(final String conf) {
        configurationReader = new ConfigurationReader(conf);
        this.gatekeeperUrl = configurationReader.readString("gatekeeper.url", makeString("https://", configurationReader.readString(
                ConfigurationKeys.COS_MONIKER),
                ".autodesk.com"));

        this.sinkUrl = makeString(configurationReader.readString("notificationsink.url", makeString("https://deployment-notify.",
                configurationReader.readString("app.moniker"),
                ".",
                ComputeConfig.isCosV3() ? "cloudos" : configurationReader.readString(ConfigurationKeys.COS_MONIKER),
                ".autodesk.com")), "/api");

        final String orderList = configurationReader.readString("service.discovery.order", "1:COSV2,2:COSV3");
        this.discoveryOrders = Splitter.on(",").withKeyValueSeparator(":").split(orderList).entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> Integer.parseInt(entry.getKey()),
                        entry -> CloudOSVersion.valueOf(entry.getValue().toUpperCase()),
                        (a, b) -> {
                            throw new IllegalStateException();
                        },
                        TreeMap::new)).values().stream().collect(Collectors.toUnmodifiableList());

        this.adfUrl = new HashMap<>();
        this.adfUrl.put(CloudOSVersion.COSV2, this.gatekeeperUrl);
        this.adfUrl.put(CloudOSVersion.COSV3, this.sinkUrl);

        this.stateMachineRoleArn = configurationReader.readString("state_machine.role_arn");

        this.awsAccessKey = configurationReader.readString(ConfigurationKeys.OVERRIDES_AWS_ACCESS_KEY, null);
        this.awsSecretKey = configurationReader.readString(ConfigurationKeys.OVERRIDES_AWS_SECRET_KEY, null);
        this.awsRegion = configurationReader.readString("aws.region", "us-west-2");
        this.portfolioVersion = configurationReader.readString("cos.portfolio.version", "deployed");
        this.appMoniker = configurationReader.readString("app.moniker", "fpccomp-c-uw2-sb");

        this.adfUpdateSNSTopicARN = configurationReader.readString("sns.topic.arn");
        this.adfSubscriptionEndpoint = configurationReader.readString("sns.topic.subscriptionurl");

        this.vaultAddress = configurationReader.readString(ConfigurationKeys.VAULT_VAULT_ADDR);

        // We do not need ans et vault username and password for cosv3
        this.vaultUsername = ComputeConfig.isCosV3() ? "n/a" : this.readSecret(configurationReader.readString("vault.username"));
        this.vaultPassword = ComputeConfig.isCosV3() ? "n/a" :this.readSecret(configurationReader.readString("vault.password"));

        if (Files.exists(Paths.get("REVISION"))) {
            try {
                this.revision = Files.readAllLines(Paths.get("REVISION")).stream().collect(Collectors.joining());
            } catch (final IOException e) {
                throw new ComputeException(ComputeErrorCodes.CONFIGURATION, e);
            }
        } else {
            this.revision = "LOCAL";
        }
    }
}
