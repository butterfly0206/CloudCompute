package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class Heartbeat {

    private String jobID;
    private String jobSecret;

    /**
     *
     **/

    @ApiModelProperty(required = true, value = "")
    @JsonProperty("jobID")
    @NotNull
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

    @ApiModelProperty(required = true, value = "")
    @JsonProperty("jobSecret")
    @NotNull
    public String getJobSecret() {
        return jobSecret;
    }

    public void setJobSecret(final String jobSecret) {
        this.jobSecret = jobSecret;
    }


    @Override
    public boolean equals(final java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Heartbeat heartbeat = (Heartbeat) o;
        return Objects.equals(jobID, heartbeat.jobID) &&
                Objects.equals(jobSecret, heartbeat.jobSecret);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobID, jobSecret);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class Heartbeat {\n");

        sb.append("    jobID: ").append(toIndentedString(jobID)).append("\n");
        sb.append("    jobSecret: ").append(toIndentedString("suppressed")).append("\n");
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

