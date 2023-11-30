package com.autodesk.compute.jobmanager.api;

import com.autodesk.compute.auth.vault.OptionalVaultAuth;
import com.autodesk.compute.common.model.HealthCheckResponse;
import com.autodesk.compute.jobmanager.api.impl.HealthcheckApiServiceImpl;
import io.swagger.annotations.ApiParam;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/healthcheck")


@io.swagger.annotations.Api(description = "the healthcheck API")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class HealthcheckApi {

    private final HealthcheckApiService service;

    public HealthcheckApi() {
        service = new HealthcheckApiServiceImpl();
    }

    @GET


    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "Health Check", notes = "Returns the health of the Service with details. If health is OK, returns 200.", response = HealthCheckResponse.class, tags = {"admins",})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "success", response = HealthCheckResponse.class)})
    @OptionalVaultAuth
    public Response healthcheckGet(@ApiParam(value = "") @HeaderParam("x-vault-token") String xVaultToken, @ApiParam(value = "") @HeaderParam("x-user") String xUser, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.healthcheckGet(xVaultToken, xUser, securityContext);
    }
}
