package com.autodesk.compute.util;

import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.Json;
import com.autodesk.compute.common.model.*;
import com.autodesk.compute.model.WorkerOutput;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.autodesk.compute.model.cosv2.DeploymentDefinition;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.pivovarit.function.ThrowingFunction;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.autodesk.compute.common.ComputeStringOps.makeString;
import static com.google.common.base.MoreObjects.firstNonNull;

@Slf4j
@UtilityClass
public class JsonHelper {

    private static final String STATUS = "status";
    private static final String TIMESTAMP = "timestamp";

    @Value
    @Builder
    private static final class InvalidJsonError {
        private String errorDetails;
    }

    public static Failure parseFailure(final String err) {
        final Failure failure = new Failure();
        //read into failure
        try {
            final JsonNode jNode = Json.mapper.readTree(err);

            // This is bad when it happens - it means there's an error record in dynamo that parses but isn't
            // an object.
            if (jNode == null) {
                log.warn("Invalid json found in error record in dynamo; this string |{}|", err);
                failure.setError("Invalid json in error in job record");
                failure.setDetails(Json.mapper.createObjectNode());
                return failure;
            }

            final Object error = jNode.get("error").asText("No error message specified");
            final Object details = jNode.get("details");
            failure.setError(error.toString());
            failure.setDetails(details);
            final String timestamp = parseTimestamp(jNode.get(TIMESTAMP));
            if (timestamp != null)
                failure.setTimestamp(timestamp);

        } catch (final JsonProcessingException e) {
            failure.setError(err);
            failure.setDetails(Json.mapper.createObjectNode());
        }
        return failure;
    }

    public static String parseTimestamp(final JsonNode jsonNode) {
        if (jsonNode == null)
            return null;

        if (jsonNode.isLong() || jsonNode.isInt()) {

            // Classically we were serializing timestamps as longs, now they are ISO strings
            // But since we have a lot of records with timestamps as longs, we need to handle those gracefully
            return Instant.ofEpochMilli(jsonNode.asLong()).atZone(ZoneOffset.UTC).toString();
        }

        if (!(jsonNode.isTextual()))
            return null;

        String timestamp = jsonNode.asText();
        if (StringUtils.isNumeric(timestamp)) {
            // For a day or so we were serializing timestamps as numeric strings
            // This code gracefully handles those records without complaint
            timestamp = Instant.ofEpochMilli(Long.parseLong(jsonNode.asText())).atZone(ZoneOffset.UTC).toString();
        }
        return timestamp;
    }

    public static StatusUpdate parseStatusUpdate(final String updateStr) {
        final StatusUpdate statusUpdate = new StatusUpdate();
        try {
            final JsonNode jsonNode = Json.mapper.readTree(updateStr);

            final ThrowingFunction<String, Status, IllegalArgumentException> statusValueOf = Status::valueOf;
            statusUpdate.setStatus(Optional.ofNullable(jsonNode.get(STATUS))
                    .map(st -> st.asText())
                    .flatMap(statusValueOf.lift())
                    .orElse(Status.UNKNOWN));

            statusUpdate.setTimestamp(Optional.ofNullable(jsonNode.get(TIMESTAMP))
                    .map(ts -> ts.asLong())
                    .map(ts -> Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC))
                    .orElse(Instant.now().atZone(ZoneOffset.UTC))
                    .toString());
        } catch (final JsonProcessingException exception) {
            log.error(makeString("Unable to parse status update: ", updateStr), exception);
            statusUpdate.setStatus(Status.UNKNOWN);
            statusUpdate.setTimestamp(Instant.now().atZone(ZoneOffset.UTC).toString());
        }

        return statusUpdate;
    }

    public Job convertToJob(final JobDBRecord record) {
        final JobInfo jobInfo = convertToJobInfo(record);
        final Job job = new Job();
        job.setWorker(record.getWorker());
        job.setService(record.getService());
        job.setPortfolioVersion(record.getPortfolioVersion());

        try {
            copyMatchingFields(jobInfo, job);
        } catch (final NoSuchFieldException | IllegalAccessException e) {
            log.error("convertToJob: unexpected error", e);
        }
        return job;
    }

    public static void copyMatchingFields(final Object fromObj, final Object toObj) throws NoSuchFieldException, IllegalAccessException {
        if (fromObj == null || toObj == null) return;

        final Field[] fields = fromObj.getClass().getDeclaredFields();
        for (final Field f : fields) {
            final Field t = toObj.getClass().getDeclaredField(f.getName());
            if (t.getType() == f.getType()) {
                f.setAccessible(true);
                t.setAccessible(true);
                t.set(toObj, f.get(fromObj));
            }
        }
    }


    public JobInfo convertToJobInfo(final JobDBRecord record) {
        final JobInfo job = new JobInfo();
        job.setJobID(record.getJobId());

        //copy all the attributes
        job.setStatus(Status.valueOf(record.getStatus()));
        Optional.ofNullable(record.findPayload()).ifPresent(p -> {
            try {
                job.setPayload(Json.mapper.readTree(p));
            } catch (final JsonProcessingException e) {
                job.setPayload(InvalidJsonError.builder()
                        .errorDetails(makeString("ERROR decoding payload, invalid JSON format '", p, "'")
                        ).build());
            }
        });

        job.setErrors(extractFailures(record));

        job.setTags(new ArrayList<>(Optional.ofNullable(record.getTags())
                .orElse(Collections.emptyList())));

        final String foundResult = record.findResult();
        if (foundResult != null) {
            try {
                final WorkerOutput workerOutput = Json.mapper.readValue(foundResult, WorkerOutput.class);
                if (workerOutput != null && workerOutput.getResult() != null) {
                    job.setResult(workerOutput.getResult());
                } else {
                    job.setResult(Json.mapper.readTree(foundResult));
                }
            } catch (final java.io.IOException e) {
                job.setResult(InvalidJsonError.builder()
                        .errorDetails(makeString("ERROR decoding result, invalid JSON format '", foundResult, "'")
                        ).build());
            }
        }

        Optional.ofNullable(record.getPercentComplete()).ifPresent(p -> {
            final JobProgress progress = new JobProgress();
            progress.setPercent(p);
            Optional.ofNullable(record.findDetails()).ifPresent(d -> {
                try {
                    progress.setDetails(Json.mapper.readTree(d));
                } catch (final JsonProcessingException e) {
                    progress.setDetails(InvalidJsonError.builder()
                            .errorDetails(makeString("ERROR decoding details, invalid JSON format '",
                                    d, "'")
                            ).build());
                }
            });
            job.setProgress(progress);
        });
        job.setCreationTime(Instant.ofEpochMilli(record.getCreationTimeMillis()).atZone(ZoneOffset.UTC).toString());
        job.setModificationTime(Long.toString(record.getModificationTime()));
        job.setTagsModificationTime(Long.toString(firstNonNull(record.getTagsModificationTime(), record.getCreationTime())));
        job.setServiceClientId(record.getServiceClientId());

        job.setStatusUpdates(Optional.ofNullable(record.getStatusUpdates())
                .orElse(Collections.emptyList())
                .stream()
                .filter(us -> !ComputeStringOps.isNullOrEmpty(us))
                .map(JsonHelper::parseStatusUpdate)
                .collect(Collectors.toList()));

        return job;
    }

    public static List<Failure> extractFailures(final JobDBRecord record) {
        return Optional.ofNullable(record.getErrors())
                .orElse(Collections.emptyList())
                .stream()
                .filter(err -> !ComputeStringOps.isNullOrEmpty(err))
                .map(JsonHelper::parseFailure)
                .collect(Collectors.toList());
    }

    public static boolean isJsonObject(final String jsonString) {
        try {
            Json.mapper.readTree(jsonString);
        } catch (final JsonProcessingException e) {
            return false;
        }
        return true;
    }

    public static JsonNode stringToJson(final String jsonString) throws JsonProcessingException {
        try {
            return Json.mapper.readTree(jsonString);
        } catch (final JsonProcessingException e) {
            log.error("FATAL: Exception turning string into JSON", e);
            throw e;
        }
    }

    public static List<DeploymentDefinition> getDeploymentsFromAdf(final AppDefinition adf) {
        return (adf.metadata != null) ? adf.metadata.deployments : Collections.emptyList();
    }

}
