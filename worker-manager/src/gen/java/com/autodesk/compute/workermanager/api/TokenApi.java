package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.auth.basic.OptionalBasicAuth;
import com.autodesk.compute.common.model.AcquireTokenArguments;
import com.autodesk.compute.common.model.NoTokenResponse;
import com.autodesk.compute.common.model.TokenResponse;
import com.autodesk.compute.workermanager.api.impl.TokenApiServiceImpl;
import io.swagger.annotations.ApiParam;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import javax.inject.Inject;

@Path("/token")


@io.swagger.annotations.Api(description = "the token API")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class TokenApi {

    @Inject
    public TokenApi() {
        service = new TokenApiServiceImpl();
    }
    TokenApiService service;

    @POST

    @Consumes({"application/json"})
    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "Acquire an auth token and (optionally) refresh it.", response = TokenResponse.class, tags = {})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Worker token", response = TokenResponse.class),

            @io.swagger.annotations.ApiResponse(code = 400, message = "JobId or JobSecret are invalid, or trying to get a token for a completed job", response = Error.class),

            @io.swagger.annotations.ApiResponse(code = 401, message = "Authentication information is missing or invalid", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 404, message = "No token found", response = NoTokenResponse.class),

            @io.swagger.annotations.ApiResponse(code = 200, message = "unexpected error", response = Error.class)})
    public Response acquireToken(@ApiParam(value = "", required = true) @NotNull @Valid final AcquireTokenArguments acquireTokenArguments, @Context final SecurityContext securityContext)
            throws NotFoundException {
        return service.acquireToken(acquireTokenArguments, securityContext);
    }

    @GET


    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "Get auth token and optionally refresh existing token and get it.", response = TokenResponse.class, tags = {})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "Worker token", response = TokenResponse.class),

            @io.swagger.annotations.ApiResponse(code = 400, message = "Job Id is invalid or trying to get a token for a completed job", response = Error.class),

            @io.swagger.annotations.ApiResponse(code = 401, message = "Authentication information is missing or invalid", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 404, message = "No token found", response = NoTokenResponse.class),

            @io.swagger.annotations.ApiResponse(code = 200, message = "unexpected error", response = Error.class)})
    @OptionalBasicAuth
    public Response getToken(@NotNull @Pattern(regexp = "[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}(:array(:(0|([1-9]\\d{0,3})))?)?") @QueryParam("jobID") final String jobID, @NotNull @QueryParam("jobSecret") final String jobSecret, @QueryParam("refresh") final Boolean refresh, @Context final SecurityContext securityContext)
            throws NotFoundException {
        return service.getToken(jobID, jobSecret, refresh, securityContext);
    }
}
