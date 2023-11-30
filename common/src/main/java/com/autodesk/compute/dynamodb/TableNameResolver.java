package com.autodesk.compute.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.model.dynamodb.IdempotentJobDBRecord;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class TableNameResolver extends DynamoDBMapperConfig.DefaultTableNameResolver {
    private final Map<Class<?>, String> classToTableNameMap;

    public TableNameResolver(final IDynamoDBJobClientConfig config) {
        this(ImmutableMap.<Class<?>, String>builder()
                .put(JobDBRecord.class, config.getDbProgressTable())
                .put(IdempotentJobDBRecord.class, config.getIdempotentJobsTable())
                .build());
    }

    public TableNameResolver(final Map<Class<?>, String> classToTableNameMap) {
        super();
        this.classToTableNameMap = new HashMap<>(classToTableNameMap);
    }

    @Override
    public String getTableName(final Class<?> clazz, final DynamoDBMapperConfig config) {
        if (!classToTableNameMap.containsKey(clazz)) {
            log.error(ComputeStringOps.makeString("TableNameResolver: couldn't find class ", clazz));
        }
        return classToTableNameMap.get(clazz);
    }
}