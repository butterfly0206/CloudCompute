package com.autodesk.compute.discovery;

import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.ComputeUtils;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.model.Services;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.autodesk.compute.model.cosv2.ApplicationDefinitions;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.api.Auth;
import com.bettercloud.vault.response.AuthResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SortedSetMultimap;
import com.pivovarit.function.ThrowingSupplier;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.autodesk.compute.common.ComputeUtils.apiCallWithRetry;
import static com.autodesk.compute.common.ServiceWithVersion.DEPLOYED;
import static com.autodesk.compute.common.ServiceWithVersion.makeKey;

@Slf4j
public class ServiceDiscovery {

    private static final String CONTENT_TYPE = "Content-Type";
    // We absolutely do not want vaultToken for cosv3, do not instantiate since it is scheduled to autoload token once instantiated.
    private static final Optional<VaultToken> vaultToken = Optional.ofNullable(ComputeConfig.isCosV3()? null : new VaultToken());
    private static final List<Integer> nonErrorResponses = Lists.newArrayList(200, 404);

    // The previous successful call from GK to get all APIs is stored here.
    // We will return a fallback version if the current API
    // call fails.
    private ApplicationDefinitions previousDeployedAdfs;    //NOPMD
    private Instant previousResultTime;                     //NOPMD
    private final Metrics metrics;

    public ServiceDiscovery() {
        this(Metrics.getInstance());
    }

    public ServiceDiscovery(final Metrics metrics) {
        this.metrics = metrics;
    }

    public static class VaultToken {
        private final ServiceDiscoveryConfig conf = ServiceDiscoveryConfig.getInstance();
        // Refreshes vault token 5 minutes before its expiration which is 2h.
        private static final int RELOAD_SCHEDULE_DURATION_IN_MINUTES = 120 - 5;

        private final AtomicReference<String> theToken = new AtomicReference<>();

        VaultToken() {
            final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            scheduledExecutorService.scheduleWithFixedDelay(this::load, 0, RELOAD_SCHEDULE_DURATION_IN_MINUTES, TimeUnit.MINUTES);
        }

        private void load() {
            try {
                // Get our root token by logging in with username/password.  Poor form to cache this.
                final VaultConfig config = new VaultConfig().address(conf.getVaultAddress()).build();
                final Vault vault = new Vault(config);
                AuthResponse response = vault.auth().loginByUserPass(conf.getVaultUsername(), conf.getVaultPassword(), "no-mfa-ldap");

                // Now use that to generate a shorter lived token we can cache
                config.token(response.getAuthClientToken());
                response = vault.auth().createToken(new Auth.TokenRequest().ttl("2h"));

                theToken.set(response.getAuthClientToken());

            } catch (final VaultException e) {
                ServiceDiscovery.log.error("VaultToken.load", e);
            }
        }

        public String getToken() {
            return theToken.get();
        }

        public String refresh() {
            load();
            return getToken();
        }
    }

    public static final class PortfolioVersionComparator implements Comparator<String> {

        /**
         * These version strings are expected to be numbers separated by dots.
         * The only valid version not of this form is "Unknown", which is equivalent to 0
         */
        @Override
        public int compare(final String o1Arg, final String o2Arg) {

            final List<ServiceDiscoveryConfig.CloudOSVersion> discoverOrder = ServiceDiscoveryConfig.getInstance().getDiscoveryOrders();
            ServiceDiscoveryConfig.CloudOSVersion o1version = ServiceDiscoveryConfig.CloudOSVersion.COSV2;
            ServiceDiscoveryConfig.CloudOSVersion o2version = ServiceDiscoveryConfig.CloudOSVersion.COSV2;
            String o1 = o1Arg;
            String o2 = o2Arg;
            if (AppDefinition.UNKNOWN_PORTFOLIO_VERSION.equals(o1)) {
                o1 = "0";
            }
            if (AppDefinition.UNKNOWN_PORTFOLIO_VERSION.equals(o2)) {
                o2 = "0";
            }
            if (AppDefinition.TEST_PORTFOLIO_VERSION.equals(o1)) {
                o1 = "-2";
                o1version = ServiceDiscoveryConfig.CloudOSVersion.COSV3;
            }
            if (AppDefinition.TEST_PORTFOLIO_VERSION.equals(o2)) {
                o2 = "-2";
                o2version = ServiceDiscoveryConfig.CloudOSVersion.COSV3;
            }
            if (AppDefinition.RELEASE_PORTFOLIO_VERSION.equals(o1)) {
                o1 = "-1";
                o1version = ServiceDiscoveryConfig.CloudOSVersion.COSV3;
            }
            if (AppDefinition.RELEASE_PORTFOLIO_VERSION.equals(o2)) {
                o2 = "-1";
                o2version = ServiceDiscoveryConfig.CloudOSVersion.COSV3;
            }

            // compare version against discoveryorder firstly
            final int o1Order = discoverOrder.contains(o1version) ? discoverOrder.indexOf(o1version) : discoverOrder.size();
            final int o2Order = discoverOrder.contains(o2version) ? discoverOrder.indexOf(o2version) : discoverOrder.size();

            final int versionDiff = o1Order - o2Order; // order is reversed
            if (versionDiff != 0) {
                return versionDiff;
            }

            final String[] o1Strings = o1.split("\\.");
            final String[] o2Strings = o2.split("\\.");
            for (int i = 0; i < Math.min(o1Strings.length, o2Strings.length); i++) {
                final int diff = Integer.parseInt(o2Strings[i]) - Integer.parseInt(o1Strings[i]);
                if (diff != 0)
                    return diff;
            }
            // Convention: the shorter length is before the longer one.
            return o2Strings.length - o1Strings.length;
        }
    }

    /**
     * Get the AppDefinitions that have a computeSpecification parameter set
     * @return Map of appDef moniker to AppDefinition class
     * @throws ComputeException
     */
    public Services getDeployedComputeServices() throws ComputeException {

        final List<ServiceDiscoveryConfig.CloudOSVersion> discoverOrder = ServiceDiscoveryConfig.getInstance().getDiscoveryOrders();
        // reverse order so that higher entry overwrite lower entry
        final SortedSetMultimap<String, AppDefinition> sortedAllADFs = createServiceSortedMultimap();
        for (final ServiceDiscoveryConfig.CloudOSVersion version : discoverOrder) {
            final ApplicationDefinitions adfs = getDeployedADFs(version);
            if (adfs != null) {
                final Multimap<String, AppDefinition> sortedADFs = getServicesFromAppDefinitions(adfs.appDefinitions);
                sortedAllADFs.putAll(sortedADFs);
            }

        }
        return new Services(sortedAllADFs);
    }

    // Public so the tests can use it
    public static SortedSetMultimap<String, AppDefinition> createServiceSortedMultimap() {
        return MultimapBuilder
        .SortedSetMultimapBuilder
        .hashKeys().treeSetValues(
                (AppDefinition o1, AppDefinition o2) -> {
                    final PortfolioVersionComparator comparer = new PortfolioVersionComparator();
                    final String DEFAULT_PORTFOLIO_VERSION = "1";
                    final String left = Optional.ofNullable(o1.getPortfolioVersion()).orElse(DEFAULT_PORTFOLIO_VERSION);
                    final String right = Optional.ofNullable(o2.getPortfolioVersion()).orElse(DEFAULT_PORTFOLIO_VERSION);
                    return comparer.compare(left, right);
                }
        ).build();
    }

    // Public so the tests can use it. Codacy objects to package-private
    public static Multimap<String, AppDefinition> getServicesFromAppDefinitions(final Collection<AppDefinition> appDefs) {
        final SortedSetMultimap<String, AppDefinition> sortedADFs = createServiceSortedMultimap();
        if (appDefs == null || appDefs.isEmpty())
            return sortedADFs;

        final Stream<AppDefinition> computeADFStream = appDefs.stream().filter(adf -> CollectionUtils.isNotEmpty(adf.getBatches()))
                .filter(batchADF -> batchADF.getBatches().stream()
                        .anyMatch(oneBatch -> oneBatch.getComputeSpecification() != null));
        final Stream<AppDefinition> componentADFStream = appDefs.stream().filter(adf -> CollectionUtils.isNotEmpty(adf.getComponents()))
                .filter(compADF -> compADF.getComponents().stream()
                        .anyMatch(oneComp -> oneComp.getComputeSpecification() != null));

        final List<AppDefinition> computeADFs = Stream.concat(computeADFStream, componentADFStream).collect(Collectors.toList());

        // ComputeADFs winds up with two kinds of keys in it:
        // 1) Stack-ranked lists of <application-moniker> -> <all ADFs for all-portfolio-versions-for-that-moniker,sorted>
        // 2) <application-moniker-with-portfolio-version-included> -> ADF for that portfolio version.
        // The combination allows callers to ask either for an explicit portfolio version of the moniker, or 'latest'.
        computeADFs.forEach(oneAdf -> {
            sortedADFs.put(makeKey(oneAdf.getAppName()), oneAdf);
            sortedADFs.put(makeKey(oneAdf.getAppName(), oneAdf.getPortfolioVersion()), oneAdf);
        });
        return sortedADFs;
    }

    // Lambdas and exceptions don't mix well -
    private ComputeUtils.PrepareForRetryResponse prepareForRetry(final int httpStatusCode) {
        try {
            if (httpStatusCode == 401 && vaultToken.isPresent())
                vaultToken.get().refresh();
            else if (httpStatusCode == 404)   // GET /v1/api/v1/<moniker>/<portfolioVersion> might return 404
                return ComputeUtils.PrepareForRetryResponse.DONT_BOTHER_RETRYING;
        } catch (final RuntimeException e) {
            log.error("prepareForRetry", e);
        }
        return ComputeUtils.PrepareForRetryResponse.RETRY_OK;
    }

    // This request needs to complete in less than 10 seconds to allow
    // clients submitting jobs through Apigee to get a response in time.
    public HttpResponse<String> makeAdfRequest(final String url, final ComputeUtils.TimeoutSettings settings)
            throws IOException, InterruptedException {
        log.info("makeAdfRequest: calling {} with timeout settings {}", url, settings);
        metrics.reportGatekeeperApiCall();
        int readTimeout = 50;
        if (settings == ComputeUtils.TimeoutSettings.FAST) {
            readTimeout = 5;
        }
        // Still seeing runs where we get a null from getToken() below, indicating
        // the vault loader worker thread didn't fire before the main thread got here
        // Coding a check and a call to refresh which will force a .load() on the token
        // Otherwise, we'd fail with a NPE internal to the .setHeader call below, which
        // previously just had vaultToken.getToken()
        String token = "";
        if(vaultToken.isPresent()) {
            token = vaultToken.get().getToken() == null ? vaultToken.get().refresh() : vaultToken.get().getToken();
        }

        final HttpRequest request = HttpRequest.newBuilder().GET()
                .uri(URI.create(url))
                .setHeader(CONTENT_TYPE, "application/json")
                .setHeader("X-Vault-Token", token)
                .timeout(Duration.of(readTimeout, ChronoUnit.SECONDS))
                .build();
        return ComputeUtils.executeRequestForJavaNet(request, settings);
    }

    public HttpResponse<String> getAllDeployedAdfs(final ServiceDiscoveryConfig.CloudOSVersion CloudOSVersion) throws IOException, InterruptedException {
        final ServiceDiscoveryConfig conf = ServiceDiscoveryConfig.getInstance();
        return makeAdfRequest(makeString(conf.getAdfUrl().get(CloudOSVersion), "/v1/apps"), ComputeUtils.TimeoutSettings.SLOW);
    }

    public HttpResponse<String> getOneAdf(@NonNull final String service, final String portfolioVersion, final ServiceDiscoveryConfig.CloudOSVersion CloudOSVersion)
            throws IOException, InterruptedException {
        final ServiceDiscoveryConfig conf = ServiceDiscoveryConfig.getInstance();
        final String url = ComputeStringOps.isNullOrEmpty(portfolioVersion) ?
                makeString(conf.getAdfUrl().get(CloudOSVersion), "/v1/apps/", service) :
                makeString(conf.getAdfUrl().get(CloudOSVersion), "/v1/apps/", service, "/", portfolioVersion);
        return makeAdfRequest(url, ComputeUtils.TimeoutSettings.FAST);
    }

    // Get and parse the results - refactor this so that it's easier to mock
    public ApplicationDefinitions parseGetAllDeployedAdfs(final ServiceDiscoveryConfig.CloudOSVersion CloudOSVersion) throws ComputeException {
        // apiCallWithRetry might throw; or we might have bad data. All of those throw, so that
        // the previously read data might be returned instead.

        final ThrowingSupplier<HttpResponse<String>, Exception> getAllAdfs = () -> getAllDeployedAdfs(CloudOSVersion);
        final Optional<HttpResponse<String>> adfsResponse = apiCallWithRetry(getAllAdfs.uncheck(), this::prepareForRetry, ComputeUtils.TimeoutSettings.SLOW, "getAllDeployedAdfs");

        // This will be null on socket timeout errors
        if (!adfsResponse.isPresent())
            return null;

        if (!nonErrorResponses.contains(adfsResponse.get().statusCode())) {
            throw new ComputeException(ComputeErrorCodes.NOT_AUTHORIZED, adfsResponse.get().body());
        }

        if (adfsResponse.get().statusCode() != 404) {
            // If we are doing a rolling deployment, then the ADF returned might not have the portfolio
            // version that we requested. https://jira.autodesk.com/browse/COSV2-3197 tracks this.
            try {
                return new ObjectMapper().readValue(adfsResponse.get().body(), ApplicationDefinitions.class);
            } catch (final JsonProcessingException e) {
                log.error("Json parsing error processing GK response: " + e.getMessage(), e);
                return null;
            }
        }

        return null;
    }

    // Get the list of ApplicationDefinition files from Gatekeeper
    // Public so it can be mocked

    public ApplicationDefinitions getDeployedADFs(final ServiceDiscoveryConfig.CloudOSVersion CloudOSVersion) throws ComputeException {
        try {
            log.info("Fetching deployed compute ADFs from gatekeeper");
            final ApplicationDefinitions currentDefinitions = parseGetAllDeployedAdfs(CloudOSVersion);
            // This is an unexpected case: GK should always return something (like, for example, Compute itself.)
            if (CloudOSVersion == ServiceDiscoveryConfig.CloudOSVersion.COSV2
                    && (currentDefinitions == null || currentDefinitions.getAppDefinitions().isEmpty())) {
                throw new ComputeException(ComputeErrorCodes.SERVER_UNEXPECTED, "Unexpected lack of ADFs returned from the Gatekeeper");
            }
            // Store the current result for later use, then return it to the caller.
            previousDeployedAdfs = currentDefinitions;
            previousResultTime = Instant.now();
            metrics.reportAdfDataAge(0.0);

            return currentDefinitions;
        } catch (final Exception e) {
            // Wrap in a ComputeException -- possibly return the old data if it's available
            log.error("Unable to read ADFs. ", e);
            // Return the previous API calls.
            if (previousDeployedAdfs != null) {
                log.warn("Getting ADFs from the gatekeeper failed. Returning the old data from {}", previousResultTime.toString());
                final double adfAgeSeconds = Duration.between(previousResultTime, Instant.now()).getSeconds();
                metrics.reportAdfDataAge(adfAgeSeconds);
                return previousDeployedAdfs;
            } else {
                throw new ComputeException(ComputeErrorCodes.SERVICE_DISCOVERY_EXCEPTION, "Unable to read ADFs", e);
            }
        }
    }

    private boolean portfolioVersionMatches(final String receivedPortfolioVersion, final String requestedPortfolioVersion) {
        if (ComputeStringOps.isNullOrEmpty(requestedPortfolioVersion) || requestedPortfolioVersion.equalsIgnoreCase(DEPLOYED))
            return true;
        if (ComputeStringOps.isNullOrEmpty(receivedPortfolioVersion))
            return false;
        return receivedPortfolioVersion.equals(requestedPortfolioVersion);
    }

    public Optional<AppDefinition> getAppFromGatekeeper(@NonNull final String serviceName, final String portfolioVersion, final ServiceDiscoveryConfig.CloudOSVersion CloudOSVersion) throws ComputeException {
        Optional<HttpResponse<String>> appResponse = Optional.empty();
        // Populate the test collection with our own ADF
        // Do it quickly, because this is called synchronously behind Apigee - we have only 10 seconds.
        // The Gatekeeper API can't be relied upon to return within that time.
        final ThrowingSupplier<HttpResponse<String>, Exception> getAdf = () -> getOneAdf(serviceName, portfolioVersion, CloudOSVersion);
        appResponse = apiCallWithRetry(getAdf.uncheck(), this::prepareForRetry, ComputeUtils.TimeoutSettings.FAST, "getOneAdf");

        // This will be null on socket timeout errors
        if (!appResponse.isPresent())
            return Optional.empty();

        if (!nonErrorResponses.contains(appResponse.get().statusCode())) {
            throw new ComputeException(ComputeErrorCodes.NOT_AUTHORIZED, appResponse.get().body());
        }

        if (appResponse.get().statusCode() != 404) {
            // If we are doing a rolling deployment, then the ADF returned might not have the portfolio
            // version that we requested. https://jira.autodesk.com/browse/COSV2-3197 tracks this.
            try {
                final AppDefinition result = new ObjectMapper().readValue(appResponse.get().body(), AppDefinition.class);
                if (portfolioVersionMatches(result.getPortfolioVersion(), portfolioVersion)) {
                    return Optional.ofNullable(result);
                }
            } catch (final JsonProcessingException e) {
                log.error("Json parsing error processing GK response: " + e.getMessage(), e);
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * @param serviceName      name of service (business service, not moniker) to retrieve
     * @param portfolioVersion the portfolio version of the application; null = current deployed
     * @return gets AppDefinition by service name and portfolio from the Gatekeeper and NotificationSink, and returns it if it has
     * ComputeSpecification in it somewhere.
     * @throws ComputeException
     */
    public Optional<AppDefinition> getAppFromServiceDiscoveryOrder(@NonNull final String serviceName, final String portfolioVersion) throws ComputeException {
        // loop on discovery order until non-empty ADF is found
        // sort the discoveryorder
        Optional<AppDefinition> app = Optional.empty();
        final List<ServiceDiscoveryConfig.CloudOSVersion> discoverOrder = ServiceDiscoveryConfig.getInstance().getDiscoveryOrders();
        for (final ServiceDiscoveryConfig.CloudOSVersion version : discoverOrder) {
            app = getAppFromGatekeeper(serviceName, portfolioVersion, version);
            if (app.isPresent()) {
                break;
            }
        }
        return app;
    }

}
