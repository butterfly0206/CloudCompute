package com.autodesk.compute.workermanager.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface JobsApiService {
      Response getJob(String id,SecurityContext securityContext)
      throws NotFoundException;
}
