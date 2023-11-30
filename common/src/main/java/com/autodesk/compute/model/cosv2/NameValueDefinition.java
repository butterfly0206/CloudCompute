package com.autodesk.compute.model.cosv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NameValueDefinition<T> {

    @JsonProperty("name")
    @ApiModelProperty(required = true, value = "Name of the environment variable")
    private String name;

    @JsonProperty("value")
    @ApiModelProperty(required = true, value = "Value of the environment variable")
    private T value;
}
