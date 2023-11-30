package com.autodesk.compute.jobmanager.util;

import com.amazonaws.services.sqs.model.Message;
import com.autodesk.compute.common.ComputeSqsDriver;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.configuration.ComputeConstants;
import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.dynamodb.DynamoDBJobClient;
import com.autodesk.compute.logging.MDCFields;
import com.autodesk.compute.logging.MDCLoader;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class JobScheduler extends Thread implements AutoCloseable {
    private final JobHelper scheduleJobHelper;
    private final DynamoDBJobClient dynamoDBJobClient;
    private final ComputeSqsDriver sqsDriver;
    private final ComputeConfig conf;
    private final AtomicBoolean shuttingDown;

    private static class LazyJobSchedulerHolder { // NOPMD
        public static final JobScheduler INSTANCE = makeInstance();

        private static JobScheduler makeInstance() {
            final JobScheduler uninitialized = new JobScheduler();
            uninitialized.start();
            // Capture the final variable
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    uninitialized.close();
                } catch (final Exception e) {
                    // We're exiting; no point in logging it.
                }
            }));
            return uninitialized;
        }
    }

    public static JobScheduler getInstance() {
        return LazyJobSchedulerHolder.INSTANCE;
    }

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    @Inject
    public JobScheduler() {
        this(new JobHelper(), new DynamoDBJobClient(), new ComputeSqsDriver());
    }

    public JobScheduler(final JobHelper scheduleJobHelper, final DynamoDBJobClient dynamoDBJobClient, final ComputeSqsDriver sqsDriver) {
        this.scheduleJobHelper = scheduleJobHelper;
        this.dynamoDBJobClient = dynamoDBJobClient;
        this.sqsDriver = sqsDriver;
        shuttingDown = new AtomicBoolean(false);
        conf = ComputeConfig.getInstance();
    }

    @Override
    public void run() {
        while (!isShuttingDown()) {
            Message message;
            do {
                message = sqsDriver.receiveOneMessage(conf.getTerminalJobsSqsUrl(), 20);
            } while (message == null);

            // We are only requesting and precessing one message a time.
            if (message.getMessageAttributes().containsKey(
                    ComputeConstants.DynamoFields.QUEUE_ID)) {
                final String queueId = message.getMessageAttributes()
                        .get(ComputeConstants.DynamoFields.QUEUE_ID)
                        .getStringValue();
                final JobDBRecord jobRecord = dynamoDBJobClient.peekJob(queueId);
                if (jobRecord != null) {
                    log.info("Found a queued job, JobId: {}", jobRecord.getJobId());

                    try (final MDCLoader loader =
                                 MDCLoader.forField(MDCFields.OPERATION, "JobScheduler.run")
                                         .withField(MDCFields.WORKER, jobRecord.getWorker())
                                         .withField(MDCFields.SERVICE, jobRecord.getService())
                                         .withField(MDCFields.JOB_ID, jobRecord.getJobId())) {

                        scheduleJobHelper.scheduleJob(loader, jobRecord, null);
                        removeJobFromQueue(queueId, jobRecord);
                        sqsDriver.deleteMessage(conf.getTerminalJobsSqsUrl(), message);

                    } catch (final JobManagerException e) {
                        // In case of invalid job payload the service have been updated to
                        // newer version and we need to remove the job from queue and SQS
                        // message that trigger it.
                        final String error = ComputeStringOps.makeString(
                                "Failed to schedule job: ", jobRecord.getJobId(),
                                ", portfolio version: ", jobRecord.getPortfolioVersion(),
                                ", service: ", jobRecord.getService(),
                                ", worker: ", jobRecord.getWorker());

                        if (e.getCode() == ComputeErrorCodes.INVALID_SERVICE_NAME ||
                                e.getCode() == ComputeErrorCodes.INVALID_JOB_PAYLOAD) {
                            // Keep the message in SQS to check if next valid job can be
                            // processed.
                            log.error(ComputeStringOps.makeString(error, ", removing it from queue"), e);
                            removeJobFromQueue(queueId, jobRecord);
                            scheduleJobHelper.safelyUpdateJobStatusToFailed(jobRecord, ComputeStringOps.makeString(error,
                                    ", due to ", e.getCode() == ComputeErrorCodes.INVALID_SERVICE_NAME ? "invalid service name" : "invalid job payload"));
                        } else if (e.getCode() == ComputeErrorCodes.DYNAMODB_TRANSACTION_CONFLICT) {
                            log.warn(ComputeStringOps.makeString(error, ", due to transaction conflict during DynamoDB status update, will simply retry"), e);
                        } else {
                            log.error(error, e);
                        }
                    } catch (final Exception e) { // Make sure any unexpected exception does not
                        // break this loop.
                        log.error(ComputeStringOps.makeString(
                                "Failed to schedule jobID: ", jobRecord.getJobId(),
                                ", portfolio version: ", jobRecord.getPortfolioVersion(),
                                ", service: ", jobRecord.getService(),
                                ", worker: ", jobRecord.getWorker(),
                                ", due to unexpected exception caught while processing"), e);
                    }
                } else {
                    log.info(
                            "There is no job queued, removing SQS message");
                    sqsDriver.deleteMessage(conf.getTerminalJobsSqsUrl(), message);
                }
            }
        }
    }

    private void removeJobFromQueue(final String queueId, final JobDBRecord jobRecord) {
        try {
            dynamoDBJobClient.removeJobFromQueue(jobRecord);
        } catch (final ComputeException e) {
            log.error("Fail to remove job from queue, jobID: {}", jobRecord.getJobId(), e);
        }
    }

    @Override
    public void close() {
        shuttingDown.set(true);
    }
}
