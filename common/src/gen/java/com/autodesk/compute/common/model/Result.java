package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class Result {

    private Object result;
    private String error;
    private Object details;
    private String timestamp;

    /**
     *
     **/

    @ApiModelProperty(example = "{\"outputFilePath\":\"https://developer-dev.api.autodesk.com/oss/v2/buckets/longstestbucket1234/objects/mytestfile02.txt\"}", value = "")
    @JsonProperty("result")
    public Object getResult() {
        return result;
    }

    public void setResult(final Object result) {
        this.result = result;
    }

    /**
     * Error enum or a short description of the error
     **/

    @ApiModelProperty(example = "INVALID_MODEL", value = "Error enum or a short description of the error")
    @JsonProperty("error")
    public String getError() {
        return error;
    }

    public void setError(final String error) {
        this.error = error;
    }

    /**
     * Any specific details around the error thrown. These will be provided to the client
     **/

    @ApiModelProperty(example = "{\"boundingBox\":{\"min\":{\"x\":10,\"y\":20},\"max\":{\"x\":40,\"y\":60}}}", value = "Any specific details around the error thrown. These will be provided to the client ")
    @JsonProperty("details")
    public Object getDetails() {
        return details;
    }

    public void setDetails(final Object details) {
        this.details = details;
    }

    /**
     * The ISO8601 timestamp when the error occured.
     **/

    @ApiModelProperty(value = "The ISO8601 timestamp when the error occured.")
    @JsonProperty("timestamp")
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final String timestamp) {
        this.timestamp = timestamp;
    }


    @Override
    public boolean equals(final java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Result result = (Result) o;
        return Objects.equals(result, result.result) &&
                Objects.equals(error, result.error) &&
                Objects.equals(details, result.details) &&
                Objects.equals(timestamp, result.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, error, details, timestamp);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class Result {\n");

        sb.append("    result: ").append(toIndentedString(result)).append("\n");
        sb.append("    error: ").append(toIndentedString(error)).append("\n");
        sb.append("    details: ").append(toIndentedString(details)).append("\n");
        sb.append("    timestamp: ").append(toIndentedString(timestamp)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(final java.lang.Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

