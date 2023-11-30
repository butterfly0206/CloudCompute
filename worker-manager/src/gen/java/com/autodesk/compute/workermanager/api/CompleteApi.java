package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.auth.basic.OptionalBasicAuth;
import com.autodesk.compute.common.model.Conclusion;
import com.autodesk.compute.workermanager.api.impl.CompleteApiServiceImpl;
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

@Path("/complete")


@io.swagger.annotations.Api(description = "the complete API")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class CompleteApi {

    private final CompleteApiService service;

    public CompleteApi() {
        service = new CompleteApiServiceImpl();
    }

    @POST

    @Consumes({"application/json"})
    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "conclude job", response = Void.class, tags = {})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "response", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 400, message = "Job id is invalid or trying to modify a completed job", response = Error.class),

            @io.swagger.annotations.ApiResponse(code = 401, message = "Authentication information is missing or invalid", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 408, message = "Job timed out before reaching completion.", response = Error.class),

            @io.swagger.annotations.ApiResponse(code = 200, message = "unexpected error", response = Error.class)})
    @OptionalBasicAuth
    public Response postComplete(@ApiParam(value = "", required = true) @NotNull @Valid Conclusion data, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.postComplete(data, securityContext);
    }
}
