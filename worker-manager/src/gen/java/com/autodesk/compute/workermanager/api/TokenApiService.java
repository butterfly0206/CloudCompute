package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.common.model.AcquireTokenArguments;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface TokenApiService {
      Response acquireToken(AcquireTokenArguments acquireTokenArguments,SecurityContext securityContext)
      throws NotFoundException;
      Response getToken(String jobID,String jobSecret,Boolean refresh,SecurityContext securityContext)
      throws NotFoundException;
}
