package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class PollResponse {

    private String jobID;
    private String jobSecret;
    private Object payload;
    private List<String> tags = new ArrayList<>();

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
     * Arbitrary Json data, conforming to a schema associated with the application definition
     **/

    @ApiModelProperty(example = "{\"inputFilePath\": \"https://developer-dev.api.autodesk.com/oss/v2/buckets/longstestbucket1234/objects/mytestfile01.txt\"}", value = "Arbitrary Json data, conforming to a schema associated with the application definition")
    @JsonProperty("payload")
    public Object getPayload() {
        return payload;
    }

    public void setPayload(final Object payload) {
        this.payload = payload;
    }

    /**
     *
     **/

    @ApiModelProperty(example = "[\"simulation\",\"bicycles\"]", value = "")
    @JsonProperty("tags")
    public List<String> getTags() {
        return tags;
    }

    public void setTags(final List<String> tags) {
        this.tags = tags;
    }


    @Override
    public boolean equals(final java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PollResponse pollResponse = (PollResponse) o;
        return Objects.equals(jobID, pollResponse.jobID) &&
                Objects.equals(jobSecret, pollResponse.jobSecret) &&
                Objects.equals(payload, pollResponse.payload) &&
                Objects.equals(tags, pollResponse.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobID, jobSecret, payload, tags);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class PollResponse {\n");

        sb.append("    jobID: ").append(toIndentedString(jobID)).append("\n");
        sb.append("    jobSecret: ").append(toIndentedString("suppressed")).append("\n");
        sb.append("    payload: ").append(toIndentedString("suppressed")).append("\n");
        sb.append("    tags: ").append(toIndentedString("suppressed")).append("\n");
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

