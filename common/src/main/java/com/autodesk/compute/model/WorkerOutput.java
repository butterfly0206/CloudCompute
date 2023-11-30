package com.autodesk.compute.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Value;


/*
    Unfortunately this class is needed since some clients have a result field inside their result.
    Those clients expect the inner result to be unwrapped. We have to keep this here.
    TODO: deprecate this class.
 */
@Value
@Builder
@JsonDeserialize(builder = WorkerOutput.WorkerOutputBuilder.class)
public class WorkerOutput {

    @JsonProperty("result")
    @ApiModelProperty(required = true, value = "Result JSON object reported by the worker")
    private Object result;;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkerOutputBuilder{}
}
