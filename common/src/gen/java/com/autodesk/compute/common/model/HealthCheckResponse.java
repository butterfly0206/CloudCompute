package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class HealthCheckResponse {

    private String portfolioVersion;

    /**
     * The overall health of the compute service (worker manager or job manager)
     */
    public enum OverallEnum {
        HEALTHY("HEALTHY"),

        UNHEALTHY("UNHEALTHY"),

        DEGRADED("DEGRADED");
        private final String value;

        OverallEnum(final String value) {
            this.value = value;
        }

        @Override
        @JsonValue
        public String toString() {
            return String.valueOf(value);
        }
    }

    private OverallEnum overall;
    private String scanTime;
    private String revision;

    /**
     * The portfolio version of the worker
     **/

    @ApiModelProperty(example = "1.0.46", value = "The portfolio version of the worker")
    @JsonProperty("portfolioVersion")
    public String getPortfolioVersion() {
        return portfolioVersion;
    }

    public void setPortfolioVersion(final String portfolioVersion) {
        this.portfolioVersion = portfolioVersion;
    }

    /**
     * The overall health of the compute service (worker manager or job manager)
     **/

    @ApiModelProperty(required = true, value = "The overall health of the compute service (worker manager or job manager)")
    @JsonProperty("overall")
    @NotNull
    public OverallEnum getOverall() {
        return overall;
    }

    public void setOverall(final OverallEnum overall) {
        this.overall = overall;
    }

    /**
     * The ISO8601 timestamp representing the start of the healthcheck
     **/

    @ApiModelProperty(required = true, value = "The ISO8601 timestamp representing the start of the healthcheck")
    @JsonProperty("scanTime")
    @NotNull
    public String getScanTime() {
        return scanTime;
    }

    public void setScanTime(final String scanTime) {
        this.scanTime = scanTime;
    }

    /**
     * Current build version\\
     **/

    @ApiModelProperty(value = "Current build version\\")
    @JsonProperty("revision")
    public String getRevision() {
        return revision;
    }

    public void setRevision(final String revision) {
        this.revision = revision;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HealthCheckResponse healthCheckResponse = (HealthCheckResponse) o;
        return Objects.equals(portfolioVersion, healthCheckResponse.portfolioVersion) &&
                Objects.equals(overall, healthCheckResponse.overall) &&
                Objects.equals(scanTime, healthCheckResponse.scanTime) &&
                Objects.equals(revision, healthCheckResponse.revision);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portfolioVersion, overall, scanTime, revision);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class HealthCheckResponse {\n");

        sb.append("    portfolioVersion: ").append(toIndentedString(portfolioVersion)).append("\n");
        sb.append("    overall: ").append(toIndentedString(overall)).append("\n");
        sb.append("    scanTime: ").append(toIndentedString(scanTime)).append("\n");
        sb.append("    revision: ").append(toIndentedString(revision)).append("\n");
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

