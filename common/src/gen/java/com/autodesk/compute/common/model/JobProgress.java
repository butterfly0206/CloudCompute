package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class JobProgress {

    private Integer percent;
    private Object details;

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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JobProgress jobProgress = (JobProgress) o;
        return Objects.equals(percent, jobProgress.percent) &&
                Objects.equals(details, jobProgress.details);
    }

    @Override
    public int hashCode() {
        return Objects.hash(percent, details);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class JobProgress {\n");

        sb.append("    percent: ").append(toIndentedString(percent)).append("\n");
        sb.append("    details: ").append(toIndentedString(details)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(final Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

