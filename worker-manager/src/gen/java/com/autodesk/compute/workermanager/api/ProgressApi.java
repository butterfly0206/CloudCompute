package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.auth.basic.OptionalBasicAuth;
import com.autodesk.compute.common.model.Progress;
import com.autodesk.compute.workermanager.api.impl.ProgressApiServiceImpl;
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

@Path("/progress")


@io.swagger.annotations.Api(description = "the progress API")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class ProgressApi {

    private final ProgressApiService service;

    public ProgressApi() {
        service = new ProgressApiServiceImpl();
    }

    @POST

    @Consumes({"application/json"})
    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "Report progress in percent complete and intermediate details", response = Void.class, tags = {})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Progress reporting response", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 400, message = "Job Id is invalid or trying to modify a completed job", response = Error.class),

            @io.swagger.annotations.ApiResponse(code = 401, message = "Authentication information is missing or invalid", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 408, message = "Job attempt has timed out.", response = Error.class),

            @io.swagger.annotations.ApiResponse(code = 200, message = "unexpected error", response = Error.class)})
    @OptionalBasicAuth
    public Response postProgress(@ApiParam(value = "", required = true) @NotNull @Valid Progress progressData, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.postProgress(progressData, securityContext);
    }
}
