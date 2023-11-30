package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.auth.basic.OptionalBasicAuth;
import com.autodesk.compute.common.model.PollResponse;
import com.autodesk.compute.common.model.Worker;
import com.autodesk.compute.workermanager.api.impl.PollApiServiceImpl;
import io.swagger.annotations.ApiParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Path("/poll")


@io.swagger.annotations.Api(description = "the poll API")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class PollApi {

    private final PollApiService service;

    public PollApi() {
        service = new PollApiServiceImpl();
    }

    @POST

    @Consumes({"application/json"})
    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "Returns a job specific to the service type", response = PollResponse.class, tags = {})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Job to work on", response = PollResponse.class),

            @io.swagger.annotations.ApiResponse(code = 401, message = "Authentication information is missing or invalid", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 404, message = "No Job is found", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 200, message = "unexpected error", response = Error.class)})
    @OptionalBasicAuth
    public Response pollForJob(@ApiParam(value = "name of service to pull the jobs for", required = true) @NotNull @Valid Worker pollData, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.pollForJob(pollData, securityContext);
    }
}
