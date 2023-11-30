package com.autodesk.compute.workermanager.api;

import com.autodesk.compute.common.model.Worker;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public interface PollApiService {
    Response pollForJob(Worker pollData, SecurityContext securityContext)
            throws NotFoundException;
}
