package com.autodesk.compute.model.cosv2;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.With;

import java.util.List;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

/**
 * Compute specific definition of AppDefinition:
 * This class is just the subset of fields required by Compute to parse the computeSpecification definitions out
 * We want to be permissive on anything else to have looser coupling with the often changing ADF schema, so as to
 * minimize required redeploys and pointless breakage.
 */
@Value
@Builder
@JsonDeserialize(builder = AppDefinition.AppDefinitionBuilder.class)
public class AppDefinition {

    public static final String UNKNOWN_PORTFOLIO_VERSION = "Unknown";

    public static final String TEST_PORTFOLIO_VERSION = "test";
    
    public static final String RELEASE_PORTFOLIO_VERSION = "release";

    @JsonProperty("metadata")
    public MetadataDefinition metadata;
    @JsonProperty("appName")
    private String appName;
    @With
    @JsonProperty("portfolioVersion")
    private String portfolioVersion;
    @Singular
    @JsonProperty("components")
    private List<ComponentDefinition> components;
    @Singular
    @JsonProperty("batches")
    private List<BatchJobDefinition> batches;

    @JsonIgnore
    public boolean hasComputeSpec() {
        return (isNotEmpty(getBatches())    //NOPMD
                && getBatches().stream().anyMatch(batch -> batch.getComputeSpecification() != null))
                || (isNotEmpty(getComponents()) //NOPMD
                && getComponents().stream().anyMatch(comp -> comp.getComputeSpecification() != null));
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppDefinitionBuilder{}
}
