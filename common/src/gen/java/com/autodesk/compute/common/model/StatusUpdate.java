package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class StatusUpdate {

    private Status status;
    private String timestamp;

    /**
     *
     **/

    @ApiModelProperty(required = true, value = "")
    @JsonProperty("status")
    @NotNull
    public Status getStatus() {
        return status;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }

    /**
     * The ISO8601 timestamp representing the time when the status was updated
     **/

    @ApiModelProperty(required = true, value = "The ISO8601 timestamp representing the time when the status was updated")
    @JsonProperty("timestamp")
    @NotNull
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final String timestamp) {
        this.timestamp = timestamp;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StatusUpdate statusUpdate = (StatusUpdate) o;
        return Objects.equals(status, statusUpdate.status) &&
                Objects.equals(timestamp, statusUpdate.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, timestamp);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class StatusUpdate {\n");

        sb.append("    status: ").append(toIndentedString(status)).append("\n");
        sb.append("    timestamp: ").append(toIndentedString(timestamp)).append("\n");
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

