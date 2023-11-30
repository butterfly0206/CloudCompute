package com.autodesk.compute;

import com.amazonaws.metrics.AwsSdkMetrics;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.autodesk.compute.common.ComputeStringOps;
import com.autodesk.compute.configuration.ComputeConfig;
import com.autodesk.compute.discovery.ResourceManager;
import com.autodesk.compute.discovery.ServiceDiscoveryConfig;
import com.autodesk.compute.discovery.WorkerResolver;
import com.autodesk.compute.dynamodb.JobCache;
import com.google.common.collect.Lists;
import com.netflix.servo.publish.*;
import com.netflix.servo.publish.cloudwatch.CloudWatchMetricObserver;
import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.ws.rs.core.Application;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.core.ResteasyDeploymentImpl;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static com.autodesk.compute.common.AWSClientUtil.makeStandardClient;
import static com.autodesk.compute.configuration.ComputeConstants.MetricsNames.WORKER_THREAD_POOL_SIZE_ENV_VAR;

@Slf4j
public class BootGeneral {

    protected final UndertowJaxrsServerCustom server = new UndertowJaxrsServerCustom();

    protected BootGeneral(final Integer port, final String host) {
        final int workerThreads = Integer.parseInt(
                ComputeStringOps.firstNonEmpty(
                        System.getenv(WORKER_THREAD_POOL_SIZE_ENV_VAR), "256"), 10);
        // The default of 16 worker threads seems to be not enough.
        final Undertow.Builder serverBuilder = Undertow.builder()
                .addHttpListener(port, host).setWorkerThreads(workerThreads);
        server.start(serverBuilder);
    }

    protected DeploymentInfo deployApplication(final String appPath, final Class<? extends Application> applicationClass) {
        final ResteasyDeploymentImpl deployment = new ResteasyDeploymentImpl();
        deployment.setApplicationClass(applicationClass.getName());
        return server.undertowDeployment(deployment, appPath);
    }

    protected void deploy(final DeploymentInfo deploymentInfo) {
        server.deploy(deploymentInfo);
    }

    protected void setPoller(final String namespace) {
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        final SeContainer container = initializer.initialize();

        final ArrayList<ObjectName> jmxNames = new ArrayList<>();
        // Fail immediately if configs are not good.
        try {
            //create ComputeConfig instance
            final ComputeConfig config = ComputeConfig.getInstance();
            AwsSdkMetrics.setRegion(config.getRegion());
            jmxNames.add(new ObjectName("org.xnio:type=Xnio,provider=\"nio\",*"));
        } catch (final RuntimeException | MalformedObjectNameException e) {
            log.error("Error loading configuration", e);
            System.exit(-1);
        }

        final PollScheduler scheduler = PollScheduler.getInstance();
        scheduler.start();
        final MetricObserver cloudwatchObserver = new CloudWatchMetricObserver(
                "ServoMetrics", namespace, makeStandardClient(AmazonCloudWatchClientBuilder.standard()));
        // TODO: Add task arn to dimensions
        final JmxMetricPoller poller = new JmxMetricPoller(new LocalJmxConnector(), jmxNames, BasicMetricFilter.MATCH_ALL);

        final PollRunnable runnable = new PollRunnable(poller, BasicMetricFilter.MATCH_ALL, true, Lists.newArrayList(cloudwatchObserver));
        scheduler.addPoller(runnable, 1, TimeUnit.MINUTES);
    }

    protected void checkConfiguration() {
        try {
            //create ServiceDiscoveryConfig instance
            ServiceDiscoveryConfig.getInstance();

            //Create singleton WorkerResolver
            WorkerResolver.getInstance();

            //create ResourceManager instance
            ResourceManager.getInstance();

            //create JobLoadingCache instance
            JobCache.getInstance();

        } catch (final RuntimeException e) {
            log.error("Error loading configuration", e);
            System.exit(-1);
        }
    }
}
