package com.autodesk.compute.api;

import com.autodesk.compute.configuration.ComputeException;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

@jakarta.annotation.Generated(value = "io.swagger.codegen.languages.JavaResteasyServerCodegen", date = "2018-03-01T06:37:36.997Z")
public abstract class AppUpdateApiService {
    public abstract Response appUpdateGet(SecurityContext securityContext, HttpHeaders headers, String body)
            throws ComputeException;
}
