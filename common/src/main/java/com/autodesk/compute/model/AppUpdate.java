package com.autodesk.compute.model;

import com.autodesk.compute.model.cosv2.ApiOperationType;
import com.autodesk.compute.model.cosv2.AppDefinition;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@JsonDeserialize(builder = AppUpdate.AppUpdateBuilder.class)
public class AppUpdate {

    @With
    @JsonProperty("operationType")
    public ApiOperationType operationType;
    @With
    @JsonProperty("app")
    public AppDefinition app;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppUpdateBuilder {}

}
