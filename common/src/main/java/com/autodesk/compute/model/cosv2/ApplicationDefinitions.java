package com.autodesk.compute.model.cosv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.Collection;

@Value
@Builder
@JsonDeserialize(builder = ApplicationDefinitions.ApplicationDefinitionsBuilder.class)
public class ApplicationDefinitions {

    @With
    @JsonProperty("appDefinitions")
    public Collection<AppDefinition> appDefinitions;
    @With
    @JsonProperty("errors")
    public Collection<String> errors;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApplicationDefinitionsBuilder{}
}