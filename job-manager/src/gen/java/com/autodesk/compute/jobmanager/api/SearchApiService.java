package com.autodesk.compute.jobmanager.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface SearchApiService {
      Response searchRecentJobs(String service, Integer maxResults, String fromTime, String toTime, String tag, String nextToken, SecurityContext securityContext)
              throws NotFoundException;
}
