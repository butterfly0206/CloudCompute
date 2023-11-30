package com.autodesk.compute.dynamodb;

import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.util.JobCacheLoader;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class JobCache {

    public static LoadingCache<String, JobDBRecord> makeJobLoadingCache() {
        final ComputeConfig config = ComputeConfig.getInstance();
        return makeJobLoadingCache(config.getCacheExpireAfterAccessSeconds(),
                config.getCacheRefreshAfterWriteSeconds(),
                new DynamoDBJobClient(config));
    }

    public static LoadingCache<String, JobDBRecord> makeJobLoadingCache(final Integer expire,
                                                                        final Integer refresh,
                                                                        final DynamoDBJobClient dynamoDBJobClient) {
        return CacheBuilder.newBuilder()
                .expireAfterAccess(expire, TimeUnit.SECONDS)
                .refreshAfterWrite(refresh, TimeUnit.SECONDS)
                .build(new JobCacheLoader(dynamoDBJobClient));
    }

    @Getter
    private LoadingCache<String, JobDBRecord> jobCache;

    private static class LazyJobCacheHolder {    //NOPMD
        public static final JobCache INSTANCE = makeJobCacheSingleton();

        private static JobCache makeJobCacheSingleton() {
            final JobCache singleton = new JobCache();
            singleton.initialize();
            return singleton;
        }
    }

    public static JobCache getInstance() {
        return JobCache.LazyJobCacheHolder.INSTANCE;
    }

    void initialize() {
        this.jobCache = JobCache.makeJobLoadingCache();
    }

}
