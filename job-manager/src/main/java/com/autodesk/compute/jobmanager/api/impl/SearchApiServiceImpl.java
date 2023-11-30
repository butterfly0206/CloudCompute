package com.autodesk.compute.jobmanager.api.impl;

import com.amazonaws.util.StringUtils;
import com.autodesk.compute.auth.oauth.OxygenSecurityContext;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.ThrottleCheck;
import com.autodesk.compute.common.model.Job;
import com.autodesk.compute.common.model.SearchResult;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.jobmanager.api.SearchApiService;
import com.autodesk.compute.jobmanager.util.JobManagerException;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.util.JsonHelper;
import com.google.common.base.MoreObjects;
import jakarta.enterprise.context.RequestScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

@Slf4j
@RequestScoped
public class SearchApiServiceImpl implements SearchApiService {
    private static final String SEARCH_JOBS_BY_TIME = "Search jobs by time";

    private final DynamoDBJobClient dynamoDBJobClient;
    private final ComputeConfig computeConfig;
    private static final String UNSPECIFIED = "unspecified";

    public SearchApiServiceImpl() {
        this(new DynamoDBJobClient(ComputeConfig.getInstance()));
    }

    public SearchApiServiceImpl(final DynamoDBJobClient dynamoDbJobClient) {
        this.dynamoDBJobClient = dynamoDbJobClient;
        this.computeConfig = initComputeConfig();
    }

    private ComputeConfig initComputeConfig() {
        ComputeConfig computeConfigTemp = null;
        try {
            computeConfigTemp = ComputeConfig.getInstance();
        } catch (final Exception e) {
            log.error("Bad configuration. Could not initialize computeConfig", e);
        }
        return computeConfigTemp;
    }

    private List<Job> getJobsFromJobRecords(final List<JobDBRecord> jobRecords) {
        return jobRecords.parallelStream()
                .filter(Objects::nonNull)
                .map(JsonHelper::convertToJob)
                .collect(Collectors.toList());
    }

    private JobManagerException generateSearchException(final Map<String, Object> searchResult) {
        final Object obj = searchResult.get(DynamoDBJobClient.ERROR_FIELD);

        // default exception is unexpected, so our static scanners are happy
        JobManagerException ex = new JobManagerException(ComputeErrorCodes.SERVER_UNEXPECTED, SEARCH_JOBS_BY_TIME);
        if (obj instanceof Exception) {
            final Exception e = (Exception)obj;
            if (e.getMessage().contains("ServiceUnavailableException")) {
                log.error(makeString("searchJobsByTime: results indicate a service unavailable exception, returned response: ", Json.toJsonString(searchResult)));
                ex = new JobManagerException(ComputeErrorCodes.SERVICE_UNAVAILABLE, SEARCH_JOBS_BY_TIME);
            } else if (ThrottleCheck.isThrottlingException(e)) {
                log.error(makeString("searchJobsByTime: results indicate a throttling exception, returned response: ", Json.toJsonString(searchResult)));
                ex = new JobManagerException(ComputeErrorCodes.TOO_MANY_REQUESTS, SEARCH_JOBS_BY_TIME);
            } else {
                log.error(makeString("searchJobsByTime: results indicate a server exception, returned response: ", Json.toJsonString(searchResult)));
                ex = new JobManagerException(ComputeErrorCodes.SERVER, SEARCH_JOBS_BY_TIME, e);
            }
        } else {
            log.error(makeString("searchJobsByTime: unknown error results, returned response: ", Json.toJsonString(searchResult)));
        }
        return ex;
    }

    private Boolean resultsShowError(final Map<String, Object> searchResults) {
        return searchResults != null && searchResults.containsKey(DynamoDBJobClient.ERROR_FIELD);
    }

    private Boolean resultsAreValid(final Map<String, Object> searchResults) {
        return searchResults != null && searchResults.containsKey(DynamoDBJobClient.JOBS_FIELD) && searchResults.containsKey(DynamoDBJobClient.NEXT_TOKEN_FIELD);
    }

    private Response searchJobsByTime(final String serviceId,
                                      final String nextToken,
                                      final Integer maxResults,
                                      final Long utcTimeFrom,
                                      final Long utcTimeTo,
                                      final String tag,
                                      final SecurityContext securityContext) throws JobManagerException {
        try (final MDCLoader loader = MDCLoader.forField(MDCFields.OPERATION, "searchRecentJobs")) {
            if (computeConfig == null) {
                log.error("searchJobsByTime: No configuration found, cannot validate auth context, cannot search jobs.");
                throw new JobManagerException(ComputeErrorCodes.CONFIGURATION, "No configuration found, cannot validate auth context");
            }
            final OxygenSecurityContext oxygenContext = OxygenSecurityContext.get(securityContext);
            final String clientId = oxygenContext.getClientId();
            final String userId = oxygenContext.getUserId();

            final String serviceClientId = Stream.of(userId, clientId, serviceId).filter(Objects::nonNull).findFirst().get();

            loader.withField("serviceId", MoreObjects.firstNonNull(serviceId, UNSPECIFIED));
            loader.withField("tag", MoreObjects.firstNonNull(tag, UNSPECIFIED));
            loader.withField("fromTime", utcTimeFrom.toString());
            loader.withField("toTime", utcTimeTo.toString());
            loader.withField("maxResults", maxResults.toString());

            /* todo: check ACM resource access */
            try {
                // perform the search
                log.debug(makeString("searchJobsByTime: calling dynamoDBJobClient.getJobsByTimeForPage with nextToken(parsed)=", ComputeStringOps.parseNextToken(nextToken)));
                final Map<String, Object> results = this.dynamoDBJobClient.getJobsByTimeForPage(serviceClientId, nextToken, maxResults, utcTimeFrom, utcTimeTo, Optional.ofNullable(tag));
                // Validate the results
                if (resultsShowError(results)) {
                    // throw a specific exception if we can
                    throw generateSearchException(results);
                } else if (resultsAreValid(results)) {
                    @SuppressWarnings("unchecked") final List<JobDBRecord> jobRecords = (List<JobDBRecord>) results.get(DynamoDBJobClient.JOBS_FIELD);
                    final String newNextToken = (String) results.get(DynamoDBJobClient.NEXT_TOKEN_FIELD);
                    // SearchResult requires Job objects, so we create them here
                    final List<Job> jobs = getJobsFromJobRecords(jobRecords);
                    log.info(makeString("searchJobsByTime: parsed ", jobs.size(), "/", jobRecords.size(), " jobs for this page, new nextToken(parsed)=", ComputeStringOps.parseNextToken(newNextToken)));
                    if (jobs.size() == jobRecords.size()) { // we've got a full set, good
                        // Build SearchResult, the API declared response object
                        final SearchResult searchResult = new SearchResult();
                        searchResult.setJobs(jobs);
                        searchResult.setNextToken(MoreObjects.firstNonNull(newNextToken, ""));
                        searchResult.setLastUpdateTime(utcTimeTo.toString());
                        log.info("searchJobsByTime: returning success");
                        return Response.status(Response.Status.OK).entity(searchResult).build();
                    }
                    log.error(makeString("searchJobsByTime: jobs <> jobRecords?!, returned response: ", Json.toJsonString(results)));
                    // fall through to default exception throw
                } else if (results == null) {
                    log.error("searchJobsByTime: results from dynamoDBJobClient are null");
                    // fall through to default exception throw
                } else {
                    log.error(makeString("searchJobsByTime: invalid response data, returned response: ", Json.toJsonString(results)));
                    // fall through to default exception throw
                }
                // default exception
                throw new JobManagerException(ComputeErrorCodes.BAD_REQUEST, SEARCH_JOBS_BY_TIME);
            } catch (final ComputeException e) {
                throw new JobManagerException(e.getCode(), SEARCH_JOBS_BY_TIME, e);
            }
        }
    }

    @Override
    public Response searchRecentJobs(final String service, final Integer maxResults, final String fromTime, final String toTime, final String tag, final String nextToken, final SecurityContext securityContext) throws JobManagerException {
        final Long toTimeMs;
        if (StringUtils.isNullOrEmpty(toTime)) {
            toTimeMs = Instant.now().toEpochMilli();
        } else {
            toTimeMs = Optional.of(toTime).map(String::trim).filter(NumberUtils::isParsable).map(Long::valueOf)
                    .orElseThrow(() -> new JobManagerException(ComputeErrorCodes.BAD_REQUEST, "toTime is not a number"));
        }

        final Long fromTimeMs;
        if (StringUtils.isNullOrEmpty(fromTime)) {
            fromTimeMs = Instant.now().minus(Duration.ofDays(30)).toEpochMilli();
        } else {
            fromTimeMs = Optional.of(fromTime).map(String::trim).filter(NumberUtils::isParsable).map(Long::valueOf)
                    .orElseThrow(() -> new JobManagerException(ComputeErrorCodes.BAD_REQUEST, "fromTime is not a number"));
        }

        return searchJobsByTime(service, nextToken, maxResults, fromTimeMs, toTimeMs, tag, securityContext);
    }
    
}
