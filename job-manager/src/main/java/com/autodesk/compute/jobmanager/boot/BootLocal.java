package com.autodesk.compute.jobmanager.boot;

import com.autodesk.compute.BootGeneral;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.jobmanager.api.ApiOriginFilter;
import com.autodesk.compute.jobmanager.util.JobScheduler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import jakarta.servlet.DispatcherType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BootLocal extends BootGeneral {

    public BootLocal(final Integer port, final String host) {
        super(port, host);
    }

    public static void main(final String[] args) {
        final BootLocal bootLocal = new BootLocal(8090, "0.0.0.0");
        bootLocal.setPoller("CloudOS/JobManager");

        // Start metrics processing
        Metrics.getInstance();

        // Start processing queued jobs
        JobScheduler.getInstance();

        final DeploymentInfo di = bootLocal.deployApplication("/api/v1", RestApplication.class)
            .setClassLoader(BootLocal.class.getClassLoader())
            .setContextPath("/")
            .setDeploymentName("Job Manager")
            .addFilter(new FilterInfo("/*", ApiOriginFilter.class))
            .addFilterUrlMapping("/*", "/*", DispatcherType.REQUEST);
        bootLocal.deploy(di);
        bootLocal.checkConfiguration();
    }
}
