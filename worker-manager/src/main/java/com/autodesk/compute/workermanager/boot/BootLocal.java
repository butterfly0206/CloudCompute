package com.autodesk.compute.workermanager.boot;

import com.autodesk.compute.BootGeneral;
import com.autodesk.compute.auth.WorkerCredentials;
import com.autodesk.compute.common.Metrics;
import com.autodesk.compute.workermanager.api.ApiOriginFilter;
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
        final BootLocal bootLocal = new BootLocal(8091, "0.0.0.0");
        bootLocal.setPoller("CloudOS/WorkerManager");

        // Start metrics processing
        Metrics.getInstance();
        // Load credentials
        WorkerCredentials.getInstance();

        final DeploymentInfo di = bootLocal.deployApplication("/api/v1", RestApplication.class)
                .setClassLoader(BootLocal.class.getClassLoader())
                .setContextPath("/")
                .setDeploymentName("Worker Manager")
                .addFilter(new FilterInfo("/*", ApiOriginFilter.class))
                .addFilterUrlMapping("/*", "/*", DispatcherType.REQUEST);
        bootLocal.deploy(di);
        bootLocal.checkConfiguration();
    }
}
