package com.autodesk.compute.jobmanager.api;

import com.autodesk.compute.common.model.ArrayJobArgs;
import com.autodesk.compute.common.model.JobArgs;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface JobsApiService {
      Response addTag(String jobId, String tagName, SecurityContext securityContext)
              throws NotFoundException;

      Response createJob(JobArgs jobArgs, Boolean noBatch, SecurityContext securityContext)
              throws NotFoundException;

      Response createJobs(ArrayJobArgs jobArgs, SecurityContext securityContext)
              throws NotFoundException;

      Response deleteJob(String id, SecurityContext securityContext)
              throws NotFoundException;

      Response deleteTag(String jobId, String tagName, SecurityContext securityContext)
              throws NotFoundException;

      Response getJob(String id, String nextToken, SecurityContext securityContext)
              throws NotFoundException;
}
