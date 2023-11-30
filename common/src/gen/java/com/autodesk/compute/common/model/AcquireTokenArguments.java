package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class AcquireTokenArguments {

    private String jobId;
    private String jobSecret;
    private Boolean refresh = false;

    /**
     *
     **/

    @ApiModelProperty(required = true, value = "")
    @JsonProperty("jobId")
    @NotNull
    @Pattern(regexp = "[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}(:array(:(0|([1-9]\\d{0,3})))?)?")
    public String getJobId() {
        return jobId;
    }

    public void setJobId(final String jobId) {
        this.jobId = jobId;
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

    /**
     * If true, the token will be refreshed to be usable for another hour
     **/

    @ApiModelProperty(value = "If true, the token will be refreshed to be usable for another hour")
    @JsonProperty("refresh")
    public Boolean getRefresh() {
        return refresh;
    }

    public void setRefresh(final Boolean refresh) {
        this.refresh = refresh;
    }


    @Override
    public boolean equals(final java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AcquireTokenArguments acquireTokenArguments = (AcquireTokenArguments) o;
        return Objects.equals(jobId, acquireTokenArguments.jobId) &&
                Objects.equals(jobSecret, acquireTokenArguments.jobSecret) &&
                Objects.equals(refresh, acquireTokenArguments.refresh);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, jobSecret, refresh);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class AcquireTokenArguments {\n");

        sb.append("    jobId: ").append(toIndentedString(jobId)).append("\n");
        sb.append("    jobSecret: ").append(toIndentedString("suppressed")).append("\n");
        sb.append("    refresh: ").append(toIndentedString(refresh)).append("\n");
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

