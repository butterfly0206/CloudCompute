package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.auth.basic.OptionalBasicAuth;
import com.autodesk.compute.common.model.Job;
import com.autodesk.compute.workermanager.api.impl.JobsApiServiceImpl;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/jobs")


@io.swagger.annotations.Api(description = "the jobs API")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class JobsApi {

    private final JobsApiService service;

    public JobsApi() {
        service = new JobsApiServiceImpl();
    }

    @GET
    @Path("/{id}")

    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "Returns a single job for given job ID", response = Job.class, tags = {"developers",})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "job details response", response = Job.class),

            @io.swagger.annotations.ApiResponse(code = 403, message = "Job does not belong to this worker", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 404, message = "job not found", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 409, message = "Job is in a terminal state", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 200, message = "unexpected error", response = Error.class)})
    @OptionalBasicAuth
    public Response getJob(@PathParam("id") String id, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.getJob(id, securityContext);
    }
}
