package com.autodesk.compute.jobmanager.api;

import com.autodesk.compute.auth.oauth.OxygenAuth;
import com.autodesk.compute.common.model.ArrayJob;
import com.autodesk.compute.common.model.ArrayJobArgs;
import com.autodesk.compute.common.model.Job;
import com.autodesk.compute.common.model.JobArgs;
import com.autodesk.compute.jobmanager.api.impl.JobsApiServiceImpl;
import io.swagger.annotations.ApiParam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
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

    @PUT
    @Path("/{jobId}/tags/{tagName}")
    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "Add a new tag for given job ID.", response = Void.class, tags = {"developers",})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "tag added", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 304, message = "The tag does exist already", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 404, message = "job not found", response = Void.class)})
    @OxygenAuth
    public Response addTag(@PathParam("jobId") String jobId, @PathParam("tagName") String tagName, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.addTag(jobId, tagName, securityContext);
    }

    @POST
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "Creates a job in the system", notes = "Creates a job for a particular compute worker type. Jobs are immutable from the client point of view once created and cannot be modified. A job will go through its own state machine and succeed or fail for various reasons including a worker-specific (defaults to 1 hour) or a worker no-longer heartbeating its progress (by default, required every 2 minutes). All jobs will be deleted from the system after 30 days of lifetime. Input payload for the job must comply with the JSON specification provided by the job worker developer. ", response = Job.class, tags = {"developers",})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "job created and either scheduled or queued", response = Job.class),

            @io.swagger.annotations.ApiResponse(code = 202, message = "job created but with schedule pending", response = Job.class)})
    @OxygenAuth
    public Response createJob(@ApiParam(value = "Job creation arguments", required = true) @NotNull @Valid JobArgs jobArgs, @QueryParam("noBatch") Boolean noBatch, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.createJob(jobArgs, noBatch, securityContext);
    }

    @POST
    @Path("/array")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "Creates array of jobs in the system", notes = "Creates array of jobs a particular compute worker type. Jobs are immutable from the client point of view once created and cannot be modified. A job will go through its own state machine and succeed or fail for various reasons including a worker-specific (defaults to 1 hour) or a worker no-longer heartbeating its progress (by default, required every 2 minutes). All jobs will be deleted from the system after 30 days of lifetime. Input payload for the job must comply with the JSON specification provided by the job worker developer. ", response = ArrayJob.class, tags = {"developers",})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "job created and either scheduled or queued", response = ArrayJob.class),

            @io.swagger.annotations.ApiResponse(code = 202, message = "job created but with schedule pending", response = ArrayJob.class)})
    @OxygenAuth
    public Response createJobs(@ApiParam(value = "Array Job creation arguments", required = true) @NotNull @Valid ArrayJobArgs jobArgs, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.createJobs(jobArgs, securityContext);
    }

    @DELETE
    @Path("/{id}")

    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "Deletes a single job for given job ID. A job can be deleted at any stage of its lifecycle. Since jobs are immutable, delete is synonymous to cancel and no separate cancel api is needed. ", response = Void.class, tags = {"developers",})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "job deleted", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 202, message = "Request is accepted in case of an array job and deletion is done asynchronously", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 400, message = "job id is invalid or trying to delete completed job", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 409, message = "job is already in a terminal state", response = Void.class)})
    @OxygenAuth
    public Response deleteJob(@PathParam("id") String id, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.deleteJob(id, securityContext);
    }

    @DELETE
    @Path("/{jobId}/tags/{tagName}")

    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "Delete a tag for given job ID.", response = Void.class, tags = {"developers",})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "tag removed", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 304, message = "The tag does not exist", response = Void.class),

            @io.swagger.annotations.ApiResponse(code = 404, message = "job not found", response = Void.class)})
    @OxygenAuth
    public Response deleteTag(@PathParam("jobId") String jobId, @PathParam("tagName") String tagName, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.deleteTag(jobId, tagName, securityContext);
    }

    @GET
    @Path("/{id}")

    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "", notes = "Returns a single job for given job ID", response = Job.class, tags = {"developers",})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "job details response", response = Job.class),

            @io.swagger.annotations.ApiResponse(code = 404, message = "job not found", response = Void.class)})
    @OxygenAuth
    public Response getJob(@PathParam("id") String id, @QueryParam("nextToken") String nextToken, @Context SecurityContext securityContext)
            throws NotFoundException {
        return service.getJob(id, nextToken, securityContext);
    }
}
