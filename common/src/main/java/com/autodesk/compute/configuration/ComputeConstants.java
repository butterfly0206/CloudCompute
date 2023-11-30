package com.autodesk.compute.configuration;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class ComputeConstants {  // NOPMD

    @UtilityClass
    public class GK {    //NOPMD
        // The revision file includes the git commit that produces the build
        public static final String REVISION = "REVISION";
        // The name of the local revision (i.e when running locally)
        public static final String LOCAL = "LOCAL";

    }

    @UtilityClass
    public static class DynamoFields {  //NOPMD
        public static final String PROGRESS_TABLE_NAME = "fpc_job_progress_data";
        public static final String IDEMPOTENT_JOBS_TABLE_NAME = "fpc_idempotent_jobs";
        public static final String ACTIVE_JOBS_COUNT_TABLE_NAME = "fpc_active_jobs_count";

        // DynamoDB indexes
        public static final String PROGRESS_TABLE_INDEX = "FPCIndex";
        public static final String PROGRESS_TABLE_SEARCH_INDEX = "ServiceModificationTimeIndex";
        public static final String PROGRESS_TABLE_QUEUEING_INDEX = "QueueIdEnqueueTimeIndex";
        public static final String PROGRESS_TABLE_ARRAY_JOB_INDEX = "ArrayJobIndex";


        // We have to use "PercentComplete" rather than the logical "Percent" because
        // it seems "Percent" is a reserved DynamoDB keyword.
        public static final String CURRENT_RETRY = "CurrentRetry";
        public static final String DETAILS = "Details";
        public static final String BINARY_DETAILS = "BinaryDetails";
        public static final String ERRORS = "Errors";
        public static final String HEARTBEAT_STATUS = "HeartbeatStatus";
        public static final String HEARTBEAT_TIMEOUT_SECONDS = "HeartbeatTimeoutSeconds";
        public static final String JOB_CREATED_TIME = "JobCreatedTime";
        public static final String JOB_FINISHED_TIME = "JobFinishedTime";
        public static final String TAGS_MODIFICATION_TIME = "TagsModificationTime";
        public static final String JOB_ID = "JobId";
        public static final String IDEMPOTENCY_ID = "IdempotencyId";
        public static final String JOB_STATUS_NOTIFICATION_ENDPOINT = "JobStatusNotificationEndpoint";
        public static final String JOB_STATUS_NOTIFICATION_TYPE = "JobStatusNotificationType";
        public static final String JOB_STATUS_NOTIFICATION_FORMAT = "JobStatusNotificationFormat";

        public static final String LAST_HEARTBEAT_TIME = "LastHeartbeatTime";
        public static final String MODIFICATION_TIME = "ModificationTime";
        public static final String OXYGEN_TOKEN = "OxygenToken";
        public static final String PAYLOAD = "Payload";
        public static final String BINARY_PAYLOAD = "BinaryPayload";
        public static final String PERCENT_COMPLETE = "PercentComplete";
        public static final String PORTFOLIO_VERSION = "PortfolioVersion";
        public static final String RESOLVED_PORTFOLIO_VERSION = "ResolvedPortfolioVersion";
        public static final String RESULT = "Result";
        public static final String BINARY_RESULT = "BinaryResult";
        public static final String SERVICE = "Service";
        public static final String SERVICE_CLIENT_ID = "ServiceClientId";
        public static final String USER_ID = "UserId";
        public static final String USER_TYPE = "UserType";
        public static final String BATCH_QUEUE_TYPE = "BatchQueueType";

        public static final String SPAWNED_BATCH_JOB_ID = "SpawnedBatchJobId";
        public static final String STATUS = "Status";
        // Attribute StepExecutionArn is kept for backwards compatibility to support existing jobs.
        public static final String STEP_EXECUTION_ARN = "StepExecutionArn";
        public static final String STEP_EXECUTION_ARNS = "StepExecutionArns";
        public static final String TAGS = "Tags";
        public static final String TTL = "ttl";
        public static final String IS_BATCH_JOB = "batch";
        public static final String WORKER = "Worker";
        public static final String STATUS_UPDATES = "StatusUpdates";

        // Attributes related to Job Queue
        public static final String QUEUE_ID = "QueueId";
        public static final String JOB_ENQUEUE_TIME = "EnqueueTime";
        public static final String QUEUE_INFO = "QueueInfo";
        public static final String QUEUED = "Queued";
        public static final String VERSION = "Version";
        public static final String SELECTED_FROM_QUEUE = "Selected";
        public static final String ADD_TO_QUEUE_TIMESTAMP = "AddTimestamp";
        public static final String PEEK_FROM_QUEUE_TIMESTAMP = "PeekTimestamp";
        public static final String REMOVE_FROM_QUEUE_TIMESTAMP = "RemoveTimestamp";
        public static final String LAST_UPDATED_TIMESTAMP = "updateTimestamp";

        // Attributes related to Active Jobs count
        public static final String USER_SERVICE_WORKER = "UserServiceWorker";
        public static final String COUNT = "Count";

        // Attributes related to Array jobs
        public static final String ARRAY_JOB_ID = "ArrayJobId";
        public static final String ARRAY_INDEX = "ArrayIndex";

    }

    @UtilityClass
    public static class MetricsNames {  //NOPMD
        public static final String USE_MOCK_ENV_VAR = "USE_MOCK_METRICS";
        public static final String ADF_AGE_RETURNED = "ReturnedADFAgeTime";
        public static final String ASYNCHRONOUS_JOB_CREATION_FAILED = "AsynchronousJobCreationFailed";
        public static final String GATEKEEPER_API_CALL = "GatekeeperAPICall";
        public static final String WORKER_THREAD_POOL_SIZE_ENV_VAR = "COS_WORKER_THREAD_POOL_SIZE";
        public static final String CREATE_BATCH_JOB_GATHER_SI = "CreateBatchJobGatherSI";
        public static final String CREATE_BATCH_JOB_SUBMIT = "CreateBatchJobSubmit";
        public static final String CREATE_JOB_UPDATE_MAX_RUNNING_JOB_COUNT = "CreateJobUpdateMaxRunningJobCount";
        public static final String CREATE_JOB_CHECK_FOR_THROTTLING = "CreateJobCheckForThrottling";
        public static final String CREATE_JOB_GET_WORKER_RESOURCES = "CreateJobGetWorkerResources";
        public static final String CREATE_JOB_INSERT_DB_RECORD = "CreateJobInsertDBRecord";
        public static final String CREATE_JOB_CREATE_BATCH_JOB = "CreateJobCreateBatchJob";
        public static final String CREATE_JOB_ADD_STEP_EXECUTION_ARN  = "CreateJobAddStepExecutionArn";
        public static final String GET_JOB_FROM_CACHE = "GetJobFromCache";
        public static final String GET_JOB_CALL = "GetJobAPICall";
        public static final String REFRESH_JOB_CACHE = "RefreshJobCache";
        public static final String JOB_CANCEL_FAILED = "JobCancelFailed";
        public static final String JOB_CANCEL_SUCCEEDED = "JobCancelSucceeded";
        public static final String JOB_DURATION = "JobDuration";
        public static final String JOB_FAILED = "JobFailed";
        public static final String JOB_SCHEDULE_DEFERRED_ON_CONFLICT = "JobScheduleDeferredOnConflict";
        public static final String JOB_SCHEDULED_DURATION = "JobScheduledTime";
        public static final String JOB_STARTED = "JobStarted";
        public static final String JOB_SUBMITTED = "JobSubmitted";
        public static final String ARRAY_JOB_SUBMITTED = "ArrayJobSubmitted";
        public static final String JOB_SUBMIT_FAILED = "JobSubmitFailed";
        public static final String JOB_SUCCEEDED = "JobSucceeded";
        public static final String NOBATCH_JOB_SUBMITTED = "NoBatchJobSubmitted";
        public static final String POLLING_JOB_SUBMITTED = "PollingJobSubmitted";
        public static final String POLLING_BATCH_JOB = "PollingBatchJob";
        public static final String REDIS_CACHE_ENTRY_NOT_FOUND = "RedisCacheEntryNotFound";
        public static final String REDIS_CACHE_ERROR = "RedisCacheError";
        public static final String REDIS_CACHE_HIT = "RedisCacheHit";
        public static final String REDIS_CACHE_MISS = "RedisCacheMiss";
        public static final String REDIS_CACHE_WRITE = "RedisCacheWrite";
        public static final String REDIS_CACHE_CLEARED = "RedisCacheCleared";
        public static final String SECRET_WRITTEN = "SecretWritten";
        public static final String SERVICE_INTEGRATION_JOB_SUBMITTED = "ServiceIntegrationJobSubmitted";
        public static final String TEST_JOB_SUBMITTED = "TestJobSubmitted";
        public static final String WORKER_AUTH_SUCCEEDED = "WorkerAuthSucceeded";
        public static final String WORKER_AUTH_FAILED = "WorkerAuthFailed";
        public static final String WORKER_FALLBACK_AUTH_USED = "WorkerFallbackAuthUsed";
        public static final String WORKER_HEARTBEAT = "HeartbeatSent";
        public static final String WORKER_PROGRESS = "ProgressSent";
        public static final String WORKER_NO_AUTH_HEADER = "WorkerNoAuthHeaderProvided";
    }

    @UtilityClass
    public static class WorkerDefaults {    //NOPMD
        public static final Integer DEFAULT_WORKER_HEARTBEAT_TIMEOUT_SECONDS = 120;
        public static final Integer DEFAULT_WORKER_JOB_ATTEMPTS = 1;
        public static final Integer DEFAULT_WORKER_JOB_TIMEOUT_SECONDS = 3600;
    }
}
