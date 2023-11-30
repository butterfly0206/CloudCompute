package com.autodesk.compute.api;

import com.autodesk.compute.api.impl.AppUpdateApiServiceImpl;
import com.autodesk.compute.configuration.ComputeException;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/appupdate")
public class AppUpdateApi  {
    @Inject
    private final AppUpdateApiService service;

    @Context protected SecurityContext securityContext;
    @Context protected HttpHeaders headers;

    public AppUpdateApi() {
        service = new AppUpdateApiServiceImpl();
    }

    @POST
    public Response appUpdateGet(final String body)
            throws ComputeException {
        return service.appUpdateGet(securityContext, headers, body);
    }
}
