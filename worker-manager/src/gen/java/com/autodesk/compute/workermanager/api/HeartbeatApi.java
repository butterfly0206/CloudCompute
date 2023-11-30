package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.auth.basic.OptionalBasicAuth;
import com.autodesk.compute.common.model.Heartbeat;
import com.autodesk.compute.common.model.HeartbeatResponse;
import com.autodesk.compute.workermanager.api.impl.HeartbeatApiServiceImpl;
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

@Path("/heartbeat")


@io.swagger.annotations.Api(description = "the heartbeat API")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class HeartbeatApi {

    private final HeartbeatApiService service;
    public HeartbeatApi() {
        service = new HeartbeatApiServiceImpl();
    }

    @POST

    @Consumes({"application/json"})
    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "Report that a worker is alive, and working on a specific job", response = HeartbeatResponse.class, tags = {})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Response with cancellation notification", response = HeartbeatResponse.class),

            @io.swagger.annotations.ApiResponse(code = 400, message = "Job Id is invalid or trying to modify a completed job", response = Error.class),

            @io.swagger.annotations.ApiResponse(code = 401, message = "Authentication information is missing or invalid", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 200, message = "unexpected error", response = Error.class)})
    @OptionalBasicAuth
    public Response postHeartbeat(@ApiParam(value = "", required = true) @NotNull @Valid Heartbeat data, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.postHeartbeat(data, securityContext);
    }
}
