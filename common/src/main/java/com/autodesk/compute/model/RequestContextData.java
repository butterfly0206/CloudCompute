package com.autodesk.compute.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.ws.rs.core.MultivaluedMap;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.Value;

@Value
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString()
public class RequestContextData {
    private String method;
    private String uri;
    private String forwardedFor;
    private MultivaluedMap<String, String> queryParameters;
    private MultivaluedMap<String, String> formParameters;
}
