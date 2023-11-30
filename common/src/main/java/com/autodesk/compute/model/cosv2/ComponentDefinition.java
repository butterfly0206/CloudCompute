package com.autodesk.compute.model.cosv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

/**
 * Compute specific definition of ComponentDefinition:
 * This class is just the subset of fields required by Compute to parse the computeSpecification definitions out
 * We want to be permissive on anything else to have looser coupling with the often changing ADF schema, so as to
 * minimize required redeploys and pointless breakage.
 */
@Value
@Builder
@JsonDeserialize(builder = ComponentDefinition.ComponentDefinitionBuilder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComponentDefinition {

    @JsonProperty("componentName")
    private String componentName;
    @JsonProperty("computeSpecification")
    private ComputeSpecification computeSpecification;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ComponentDefinitionBuilder{}
}
