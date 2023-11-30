package com.autodesk.compute.common;

import com.autodesk.compute.common.model.*;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.categories.UnitTests;
import com.autodesk.compute.util.JsonHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

@Category(UnitTests.class)
@Slf4j
public class TestJsonHelper {
    public static final String JOB_PAYLOAD = "{ \"Empty\": \"Payload\" }";
    public static final String JOB_RESULT = "{ \"Hello\": \"World\" }";
    public static final String JOB_DETAILS = "{ \"Details\": \"Unimportant\" }";
    public static final String JOB_RESULT_WITH_RESULT = "{ \"result\": { \"Hello\": \"World\" } }";
    public static final String JOB_ID = "aaaaaaaa-bbbb-cccc-dddd-abcdefabcdef";
    public static final List<String> JOB_TAGS = Arrays.asList("a one", "a two", "a one two", "three four");
    public static final List<String> JOB_ERRORS = Arrays.asList(
        "{ \"not a valid error \" }",
        "{ \"error\": \"no details\" }",
        "{ \"error\": \"with details\", \"details\": \"minimal\" }",
        "{ \"error\": \"with details\", \"details\": \"bogus timestamp\", \"timestamp\": \"{ \"huh\": \"for code coverage\" }\" }",
        "{ \"error\": \"with details\", \"details\": \"has stringified long timestamp\", \"timestamp\": \"600\" }",
        "{ \"error\": \"with details\", \"details\": \"has int timestamp\", \"timestamp\": 102834600 }",
        "{ \"error\": \"with details\", \"details\": \"has long timestamp\", \"timestamp\": 102834600000 }",
        "{ \"error\": \"with details\", \"details\": \"has iso timestamp\", \"timestamp\": \"2020-02-25T06:15:00Z\" }"
    );


    @Test
    public void parseVariousStatuses() {
        for (final Status oneStatus: Status.values()) {
            final StatusUpdate update = JsonHelper.parseStatusUpdate("{ \"status\": \"" + oneStatus.toString() + "\", \"timestamp\": 1578510839000 }");
            assertEquals(update.getStatus(), oneStatus);
        }

        // TODO: This can be removed after CREATED records expire in dev DynamoDB-- check again after 3/15/2020
        final StatusUpdate update = JsonHelper.parseStatusUpdate("{ \"status\": \"CREATED\", \"timestamp\": 1578510839000 }");
        assertEquals(Status.CREATED, update.getStatus());
    }

    @Test
    public void testGetCompletedJobOk() {
        final JobDBRecord completedJobRecord = buildCompletedJobRecord();
        final Job completedJob = JsonHelper.convertToJob(completedJobRecord);
        assertNotNull(completedJob.getResult());
        assertNotNull(completedJob.getProgress());
        assertNotNull(completedJob.getPayload());
        assertEquals(JOB_TAGS.size(), completedJob.getTags().size());
    }

    @Test
    public void testGetCompletedJobWithExtraResultOk() throws JsonProcessingException {
        final JobDBRecord completedJobRecord = buildCompletedJobRecordWithExtraResult();
        final Job completedJob = JsonHelper.convertToJob(completedJobRecord);
        final String resultString = Json.mapper.writeValueAsString(completedJob.getResult());

        assertNotNull(completedJob.getResult());
        assertNotNull(completedJob.getProgress());
        assertNotNull(completedJob.getPayload());
        assertFalse(resultString.contains("result"));
        assertEquals(JOB_TAGS.size(), completedJob.getTags().size());
        assertEquals(JOB_ERRORS.size(), completedJob.getErrors().size());

        final List<Pair<String, Failure>> failures = IntStream.range(0, JOB_ERRORS.size())
                .mapToObj(index -> Pair.of(JOB_ERRORS.get(index), completedJob.getErrors().get(index)))
                .collect(Collectors.toList());

        for (final Pair<String, Failure> onePair : failures) {
            final String stringError = onePair.getLeft();
            final Failure failureError = onePair.getRight();
            assertNotNull(failureError);
            assertNotNull(failureError.getError());
            if (stringError.contains("no details")) {
                assertNull(failureError.getDetails());
            }
            if (stringError.contains("with details")) {
                assertNotNull(failureError.getDetails());
                if (failureError.getDetails().toString().contains("timestamp"))
                    assertFalse(failureError.getTimestamp().isEmpty());
                else
                    assertNull(failureError.getTimestamp());
            }
        }
    }

    @Test
    public void testCopyMatchingFields() throws NoSuchFieldException, IllegalAccessException {
        final String nonEmptyString = "NOT EMPTY";
        final JobInfo jobInfo = new JobInfo();
        jobInfo.setCreationTime(nonEmptyString);
        jobInfo.setErrors(Collections.emptyList());
        jobInfo.setJobID(nonEmptyString);
        jobInfo.setModificationTime(nonEmptyString);
        jobInfo.setTagsModificationTime(nonEmptyString);
        jobInfo.setPayload(new Object());
        jobInfo.setProgress(new JobProgress());
        jobInfo.setResult(new Object());
        jobInfo.setCreationTime(nonEmptyString);
        jobInfo.setStatus(Status.CREATED);
        jobInfo.setErrors(Collections.emptyList());
        jobInfo.setTags(Collections.emptyList());
        jobInfo.setServiceClientId(nonEmptyString);

        // Validate that all fields of jobInfo are set
        validateAllFieldsAreSet(jobInfo);

        final Job job = new Job();
        // These three fields are exclusive to Job that are not present in Job
        job.setService(nonEmptyString);
        job.setWorker(nonEmptyString);
        job.setPortfolioVersion(nonEmptyString);

        // Copy rest of the fields from Job
        JsonHelper.copyMatchingFields(jobInfo, job);

        // Make sure the job has all of it's fields set
        validateAllFieldsAreSet(job);

        // Some extra validation.
        assertEquals(nonEmptyString, job.getCreationTime());
        assertEquals(nonEmptyString, job.getJobID());
        assertEquals(nonEmptyString, job.getModificationTime());
        assertEquals(nonEmptyString, job.getTagsModificationTime());
        assertEquals(nonEmptyString, job.getPortfolioVersion());
        assertEquals(nonEmptyString, job.getServiceClientId());
        assertEquals(Status.CREATED, job.getStatus());
    }

    private static JobDBRecord buildCompletedJobExceptResult() {
        // build a job record
        final JobDBRecord jobRecord = JobDBRecord.createDefault(JOB_ID);
        jobRecord.setStatus(JobStatus.COMPLETED.toString());
        jobRecord.setTags(new ArrayList<>());
        jobRecord.setPercentComplete(100);
        jobRecord.setBinaryPayload(ComputeStringOps.compressString(JOB_PAYLOAD));
        jobRecord.setBinaryDetails(ComputeStringOps.compressString(JOB_DETAILS));
        jobRecord.setOxygenToken("oxygenToken");
        jobRecord.setHeartbeatStatus(DynamoDBJobClient.HeartbeatStatus.ENABLED.toString());
        jobRecord.setStepExecutionArns(Arrays.asList("step-arn"));
        jobRecord.setHeartbeatTimeoutSeconds(120);
        jobRecord.setCurrentRetry(0);
        jobRecord.setLastHeartbeatTime(0L);
        jobRecord.setCreationTime(JobDBRecord.fillCurrentTimeMilliseconds());
        jobRecord.setService("service");
        jobRecord.setWorker("worker");
        jobRecord.setJobFinishedTime(0L);
        jobRecord.setIdempotencyId("idempotencyId");
        jobRecord.setTags(JOB_TAGS);
        jobRecord.setErrors(JOB_ERRORS);
        return jobRecord;
    }

    private static JobDBRecord buildCompletedJobRecord() {
        // build a job record
        final JobDBRecord jobRecord = buildCompletedJobExceptResult();
        jobRecord.setResult(JOB_RESULT);
        return jobRecord;
    }

    private static JobDBRecord buildCompletedJobRecordWithExtraResult() {
        // build a job record
        final JobDBRecord jobRecord = buildCompletedJobExceptResult();
        jobRecord.setResult(JOB_RESULT_WITH_RESULT);
        return jobRecord;
    }

    private static void validateAllFieldsAreSet(final Object obj) throws NoSuchFieldException, IllegalAccessException {
        final Field[] fields = obj.getClass().getDeclaredFields();
        for (final Field f : fields) {
            final Field t = obj.getClass().getDeclaredField(f.getName());
            if (t.getType() == f.getType()) {
                f.setAccessible(true);
                t.setAccessible(true);
                assertNotNull(f.toString(), f.get(obj));
            }
        }
    }

}
