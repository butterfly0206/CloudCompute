package com.autodesk.compute.completionhandler;

import com.autodesk.compute.configuration.ConfigurationReader;
import com.autodesk.compute.dynamodb.IDynamoDBJobClientConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.autodesk.compute.configuration.ComputeConstants.DynamoFields.*;

@Getter
@Slf4j
public class CompletionHandlerConfig implements IDynamoDBJobClientConfig {

    protected ConfigurationReader configurationReader;

    private final String dbProgressTable;
    private final String dbProgressTableIndex = PROGRESS_TABLE_INDEX;
    private final String dbProgressTableSearchIndex = PROGRESS_TABLE_SEARCH_INDEX;
    private final String dbProgressTableQueueingIndex = PROGRESS_TABLE_QUEUEING_INDEX;
    private final String dbProgressTableArrayJobIndex = PROGRESS_TABLE_ARRAY_JOB_INDEX;

    protected CompletionHandlerConfig() {
        configurationReader = new ConfigurationReader("conf");
        this.dbProgressTable = configurationReader.readString("dynamodb.table.name");
    }

    @Override
    public String getIdempotentJobsTable() {
        return getDbProgressTable().replace("job_progress_data", "idempotent_jobs");
    }

    @Override
    public String getActiveJobsCountTable() {
        return getDbProgressTable().replace("job_progress_data", "active_jobs_count");
    }
}
