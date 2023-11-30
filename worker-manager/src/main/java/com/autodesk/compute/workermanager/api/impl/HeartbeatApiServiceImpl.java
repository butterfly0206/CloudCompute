package com.autodesk.compute.workermanager.api.impl;

import com.autodesk.compute.common.model.Heartbeat;
import com.autodesk.compute.workermanager.api.HeartbeatApiService;
import com.autodesk.compute.workermanager.util.HeartbeatHandler;
import com.autodesk.compute.workermanager.util.WorkerManagerException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;

@Slf4j
@RequestScoped
@Default
public class HeartbeatApiServiceImpl implements HeartbeatApiService {
    private final HeartbeatHandler handler;

    @Inject
    public HeartbeatApiServiceImpl() {
        this(new HeartbeatHandler());
    }

    public HeartbeatApiServiceImpl(final HeartbeatHandler handler) {
        this.handler = handler;
    }

    @Override
    public Response postHeartbeat(final Heartbeat data, final SecurityContext securityContext)
            throws WorkerManagerException {
        return handler.processHeartbeat(data.getJobID(), data.getJobSecret(), securityContext);
    }
}
