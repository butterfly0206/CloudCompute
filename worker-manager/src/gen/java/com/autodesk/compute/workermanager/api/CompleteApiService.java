package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.common.model.Conclusion;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface CompleteApiService {
      Response postComplete(Conclusion data,SecurityContext securityContext)
      throws NotFoundException;
}
