package com.autodesk.compute.jobmanager.api;

import com.autodesk.compute.auth.oauth.OxygenAuth;
import com.autodesk.compute.common.model.SearchResult;
import com.autodesk.compute.jobmanager.api.impl.SearchApiServiceImpl;
import io.swagger.annotations.ApiParam;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@Path("/search")


@io.swagger.annotations.Api(description = "the search API")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class SearchApi {

    private final SearchApiService service;

    public SearchApi() {
        service = new SearchApiServiceImpl();
    }

    @GET
    @Path("/recent")

    @Produces({"application/json"})
    @io.swagger.annotations.ApiOperation(value = "Search recently (up to 30 days) modified jobs, regardless of status.", notes = "Returns any recent job regardless of status within the time scope. The item order in the pages returned is arbitrary. A single query operation can retrieve a variable number of items, limited by the lesser of a maximum of 1 MB of data or maxResults (# of items per page). ", response = SearchResult.class, tags = {"developers",})
    @io.swagger.annotations.ApiResponses(value = {
            @io.swagger.annotations.ApiResponse(code = 200, message = "List of recently modified jobs and possibly a NextToken object", response = SearchResult.class),

            @io.swagger.annotations.ApiResponse(code = 503, message = "Service unavailable, try again later", response = Error.class),

            @io.swagger.annotations.ApiResponse(code = 200, message = "Unexpected error encountered during the search operation", response = Error.class)})
    @OxygenAuth
    public Response searchRecentJobs(@NotNull @QueryParam("service") String service, @NotNull @DefaultValue("100") @QueryParam("maxResults") Integer maxResults, @QueryParam("fromTime") String fromTime, @QueryParam("toTime") String toTime, @QueryParam("tag") String tag, @ApiParam(value = "Internal token used for search pagination, returned in search results for queries which span multiple pages ") @HeaderParam("nextToken") String nextToken, @Context SecurityContext securityContext)
            throws NotFoundException {
        return this.service.searchRecentJobs(service, maxResults, fromTime, toTime, tag, nextToken, securityContext);
    }
}
