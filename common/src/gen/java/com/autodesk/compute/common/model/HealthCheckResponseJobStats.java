package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import java.math.BigDecimal;
import java.util.Objects;


public class HealthCheckResponseJobStats {

    private BigDecimal INPROGRESS;

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("INPROGRESS")
    public BigDecimal getINPROGRESS() {
        return INPROGRESS;
    }

    public void setINPROGRESS(final BigDecimal INPROGRESS) {
        this.INPROGRESS = INPROGRESS;
    }


    @Override
    public boolean equals(final java.lang.Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HealthCheckResponseJobStats healthCheckResponseJobStats = (HealthCheckResponseJobStats) o;
        return Objects.equals(INPROGRESS, healthCheckResponseJobStats.INPROGRESS);
    }

    @Override
    public int hashCode() {
        return Objects.hash(INPROGRESS);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class HealthCheckResponseJobStats {\n");

        sb.append("    INPROGRESS: ").append(toIndentedString(INPROGRESS)).append("\n");
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

