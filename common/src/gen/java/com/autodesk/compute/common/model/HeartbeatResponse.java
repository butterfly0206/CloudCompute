package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class HeartbeatResponse {

    private Boolean canceled;

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("canceled")
    public Boolean getCanceled() {
        return canceled;
    }

    public void setCanceled(final Boolean canceled) {
        this.canceled = canceled;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HeartbeatResponse heartbeatResponse = (HeartbeatResponse) o;
        return Objects.equals(canceled, heartbeatResponse.canceled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(canceled);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class HeartbeatResponse {\n");

        sb.append("    canceled: ").append(toIndentedString(canceled)).append("\n");
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

