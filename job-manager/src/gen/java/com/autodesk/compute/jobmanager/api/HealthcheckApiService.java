package com.autodesk.compute.jobmanager.api;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface HealthcheckApiService {
      Response healthcheckGet(String xVaultToken, String xUser, SecurityContext securityContext)
              throws NotFoundException;
}
