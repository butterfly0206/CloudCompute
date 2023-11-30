package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.Pattern;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class Progress {

    private String jobID;
    private String jobSecret;
    private Integer percent;
    private Object details;

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

    @ApiModelProperty(example = "74", value = "")
    @JsonProperty("percent")
    public Integer getPercent() {
        return percent;
    }

    public void setPercent(final Integer percent) {
        this.percent = percent;
    }

    /**
     * Arbitrary Json data, conforming to a schema associated with the application definition
     **/

    @ApiModelProperty(example = "{\"preview\": \"https://developer-dev.api.autodesk.com/oss/v2/buckets/1/preview.jpg\"}", value = "Arbitrary Json data, conforming to a schema associated with the application definition")
    @JsonProperty("details")
    public Object getDetails() {
        return details;
    }

    public void setDetails(final Object details) {
        this.details = details;
    }


    @Override
    public boolean equals(final java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Progress progress = (Progress) o;
        return Objects.equals(jobID, progress.jobID) &&
                Objects.equals(jobSecret, progress.jobSecret) &&
                Objects.equals(percent, progress.percent) &&
                Objects.equals(details, progress.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobID, jobSecret, percent, details);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class Progress {\n");

        sb.append("    jobID: ").append(toIndentedString(jobID)).append("\n");
        sb.append("    jobSecret: ").append(toIndentedString("suppressed")).append("\n");
        sb.append("    percent: ").append(toIndentedString(percent)).append("\n");
        sb.append("    details: ").append(toIndentedString(details)).append("\n");
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

