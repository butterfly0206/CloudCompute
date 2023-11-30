package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.Pattern;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class Conclusion {

    private Object result;
    private String error;
    private Object details;
    private String timestamp;
    private String jobID;
    private String jobSecret;
    private Status status;

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

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("jobID")
    @Pattern(regexp = "[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}(:array(:(0|([1-9]\\d{0,3})))?)?")
    public String getJobID() {
        return jobID;
    }

    public void setJobID(final String jobID) {
        this.jobID = jobID;
    }

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("jobSecret")
    public String getJobSecret() {
        return jobSecret;
    }

    public void setJobSecret(final String jobSecret) {
        this.jobSecret = jobSecret;
    }

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("status")
    public Status getStatus() {
        return status;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }


    @Override
    public boolean equals(final java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Conclusion conclusion = (Conclusion) o;
        return Objects.equals(result, conclusion.result) &&
                Objects.equals(error, conclusion.error) &&
                Objects.equals(details, conclusion.details) &&
                Objects.equals(timestamp, conclusion.timestamp) &&
                Objects.equals(jobID, conclusion.jobID) &&
                Objects.equals(jobSecret, conclusion.jobSecret) &&
                Objects.equals(status, conclusion.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, error, details, timestamp, jobID, jobSecret, status);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class Conclusion {\n");

        sb.append("    result: ").append(toIndentedString(result)).append("\n");
        sb.append("    error: ").append(toIndentedString(error)).append("\n");
        sb.append("    details: ").append(toIndentedString(details)).append("\n");
        sb.append("    timestamp: ").append(toIndentedString(timestamp)).append("\n");
        sb.append("    jobID: ").append(toIndentedString(jobID)).append("\n");
        sb.append("    jobSecret: ").append(toIndentedString("suppressed")).append("\n");
        sb.append("    status: ").append(toIndentedString(status)).append("\n");
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

