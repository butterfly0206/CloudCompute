package com.autodesk.compute.model.cosv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Value;

/**
 * Compute specific definition of BatchJobDefinition:
 * This class is just the subset of fields required by Compute to parse the computeSpecification definitions out
 * We want to be permissive on anything else to have looser coupling with the often changing ADF schema, so as to
 * minimize required redeploys and pointless breakage.
 */
@Value
@Builder
@JsonDeserialize(builder = BatchJobDefinition.BatchJobDefinitionBuilder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchJobDefinition {

    @JsonProperty
    private String jobDefinitionName;
    @JsonProperty("computeSpecification")
    private ComputeSpecification computeSpecification;
    @JsonProperty("jobAttempts")
    private Integer jobAttempts;
    @JsonProperty("containerProperties")
    private BatchContainerDefinition containerProperties;
    @JsonProperty("options")
    private BatchJobOptions batchJobOptions;


    @Value
    @Builder
    @JsonDeserialize(builder = BatchJobOptions.BatchJobOptionsBuilder.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BatchJobOptions {
        @JsonProperty("neverSetJobPayload")
        @ApiModelProperty(value = "If this is true, then don't set the COS_JOB_PAYLOAD environment variable in the batch job. The worker will have to call the worker manager's GET /ap1/v1/jobs API to find its payload.")
        @Builder.Default
        private Boolean neverSetJobPayload = false;

        @JsonProperty("neverSetSecretEnvironmentVariables")
        @ApiModelProperty(value = "In the batch job definitions, skip writing the environment variables that provide paths to the secrets. The worker startup will need to use SSM.GetParametersByPath to find the secrets and set them as environment variables.")
        @Builder.Default
        private Boolean neverSetSecretEnvironmentVariables = false;


        @JsonPOJOBuilder(withPrefix = "")
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class BatchJobOptionsBuilder {
        }
    }


    @Value
    @Builder
    @JsonDeserialize(builder = BatchContainerDefinition.BatchContainerDefinitionBuilder.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class BatchContainerDefinition {

        @JsonProperty("gpu")
        private Integer gpu;

        @JsonPOJOBuilder(withPrefix = "")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class BatchContainerDefinitionBuilder {
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BatchJobDefinitionBuilder {
    }
}
