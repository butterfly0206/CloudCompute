package com.autodesk.compute.model.cosv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentDefinition {

    @ApiModelProperty(required = false, value = "The account where the deployment will happen (default = same as CloudOS deployment)")
    @JsonProperty("accountId")
    private String accountId;

    @ApiModelProperty(required = false, value = "The region in the target account (default=same as CloudOS deployment")
    @JsonProperty("region")
    private String region;

}