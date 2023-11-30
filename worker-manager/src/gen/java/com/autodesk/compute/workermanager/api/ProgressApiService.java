package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.common.model.Progress;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface ProgressApiService {
      Response postProgress(Progress progressData,SecurityContext securityContext)
      throws NotFoundException;
}
