package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.common.model.Heartbeat;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface HeartbeatApiService {
      Response postHeartbeat(Heartbeat data,SecurityContext securityContext)
      throws NotFoundException;
}
