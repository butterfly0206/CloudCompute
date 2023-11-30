package com.autodesk.compute.model.cosv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * Compute specific definition of MetadataDefinition: This class is just the
 * subset of fields required by Compute to parse the metadata definitions out We
 * want to be permissive on anything else to have looser coupling with the often
 * changing ADF schema, so as to minimize required redeploys and pointless
 * breakage.
 */
@Data
@Builder
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetadataDefinition {
    @Singular
    @JsonProperty("deployments")
    public List<DeploymentDefinition> deployments;
}
