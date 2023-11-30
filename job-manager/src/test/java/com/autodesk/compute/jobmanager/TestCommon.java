package com.autodesk.compute.jobmanager;

import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.common.model.ArrayJobArgs;
import com.autodesk.compute.common.model.ArrayJobItem;
import com.autodesk.compute.common.model.JobArgs;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.dynamodb.JobStatus;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.model.dynamodb.SearchJobsResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.autodesk.compute.common.ComputeStringOps.makeString;

public class TestCommon {

    public static final String ARRAY_JOB_ID = "aaaaaaaa-bbbb-cccc-dddd-abcdefabcdef:array";
    public static final int ARRAY_JOB_SIZE = 100;
    public static final String SERVICE = "service";
    public static final String WORKER = "worker";
    public static final String PORTFOLIO_VERSION = "1.0";
    public static final String JOBID = "aaaaaaaa-bbbb-cccc-dddd-abcdefabcdef";

    public static JobArgs getJobArgs() {
        final JobArgs jobArgs = new JobArgs();
        jobArgs.setService("fpcexapp-c-uw2-sb");
        jobArgs.setWorker(WORKER);
        jobArgs.setTags(Arrays.asList("tag1", "tag2"));
        jobArgs.setPayload("{\"jobId\":\"jobId\",\"payload\":\"payload\",\"worker\":\"workers\",\"tags\":null}");
        return jobArgs;
    }

    public static ArrayJobArgs getArrayJobArgs() {
        final ArrayJobArgs jobArgs = new ArrayJobArgs();
        jobArgs.setService("fpcexapp-c-uw2-sb");
        jobArgs.setWorker(WORKER);
        final List<ArrayJobItem> jobItems = new ArrayList<>();
        for (int i = 0; i < ARRAY_JOB_SIZE; i++) {
            final ArrayJobItem item = new ArrayJobItem();
            item.setTags(Arrays.asList("index", Integer.toString(i)));
            item.setPayload("{\"jobId\":\"jobId\",\"payload\":\"payload\",\"worker\":\"workers\",\"tags\":null}");
            jobItems.add(item);
        }
        jobArgs.setJobs(jobItems);
        return jobArgs;
    }

    public static JobDBRecord buildJobRecord() {
        // build a job record
        final JobDBRecord jobRecord = JobDBRecord.createDefault("job00");
        jobRecord.setStatus(JobStatus.SCHEDULED.toString());
        jobRecord.setTags(new ArrayList<>());
        jobRecord.setPercentComplete(0);
        jobRecord.setBinaryPayload(ComputeStringOps.compressString("{}"));
        jobRecord.setOxygenToken("oxygenToken");
        jobRecord.setHeartbeatStatus(DynamoDBJobClient.HeartbeatStatus.ENABLED.toString());
        jobRecord.setHeartbeatTimeoutSeconds(120);
        jobRecord.setCurrentRetry(0);
        jobRecord.setLastHeartbeatTime(0L);
        jobRecord.setCreationTime(JobDBRecord.fillCurrentTimeMilliseconds());
        jobRecord.setService(SERVICE);
        jobRecord.setWorker(WORKER);
        jobRecord.setPortfolioVersion(PORTFOLIO_VERSION);
        jobRecord.setJobFinishedTime(0L);
        jobRecord.setIdempotencyId("idempotencyId");
        return jobRecord;
    }

    public static List<JobDBRecord> buildArrayJobRecords() {
        final List<JobDBRecord> records = new ArrayList<>();
        for (int i = 0; i < ARRAY_JOB_SIZE; i++) {
            final JobDBRecord record = buildJobRecord();
            record.setJobId(makeString(ARRAY_JOB_ID, ":", i));
            record.setBatch(true);
            records.add(record);
        }
        return records;
    }

    static SearchJobsResult buildSearchJobResult() {
        return new SearchJobsResult(buildArrayJobRecords(), null);
    }
}
