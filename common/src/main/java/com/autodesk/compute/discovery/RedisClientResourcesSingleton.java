package com.autodesk.compute.discovery;

import com.pivovarit.function.ThrowingRunnable;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DirContextDnsResolver;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class RedisClientResourcesSingleton {    //NOPMD
    // https://en.wikipedia.org/wiki/Initialization-on-demand_holder_idiom
    private static class LazyClientResourcesHolder {    //NOPMD
        public static final ClientResources INSTANCE = makeInstance();

        // This comes from: https://github.com/lettuce-io/lettuce-core/blob/5.2.0.RELEASE/src/test/java/io/lettuce/examples/ConnectToElastiCacheMaster.java
        private static ClientResources makeInstance() {
            final ClientResources singleton = DefaultClientResources.builder()
                    .dnsResolver(new DirContextDnsResolver())
                    .build();
            registerShutdownHook(singleton);
            return singleton;
        }

        private static void registerShutdownHook(final ClientResources instance) {
            final ThrowingRunnable<?> shutdownHook = () -> {
                final Future<Boolean> shuttingDown = instance.shutdown();
                try {
                    final boolean result = shuttingDown.get(10, TimeUnit.SECONDS);
                    if (!result) {
                        log.warn("Shutting down Redis ClientResources on JVM exit returned false");
                    }
                } catch (final ExecutionException | TimeoutException e) {
                    log.warn("Exception closing Redis ClientResources on JVM exit: ", e);
                }
            };
            Runtime.getRuntime().addShutdownHook(new Thread(shutdownHook.unchecked()));
        }
    }

    public static ClientResources getInstance() {
        return LazyClientResourcesHolder.INSTANCE;
    }

    private RedisClientResourcesSingleton() {
    }
}
