package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class ArrayJobArgs {

    private String service;
    private String worker;
    private String portfolioVersion;
    private List<ArrayJobItem> jobs = new ArrayList<>();

    /**
     * An appdef moniker
     **/

    @ApiModelProperty(example = "amktool-c-uw2", required = true, value = "An appdef moniker")
    @JsonProperty("service")
    @NotNull
    public String getService() {
        return service;
    }

    public void setService(final String service) {
        this.service = service;
    }

    /**
     * The particular worker within a service
     **/

    @ApiModelProperty(example = "path-gen", required = true, value = "The particular worker within a service")
    @JsonProperty("worker")
    @NotNull
    public String getWorker() {
        return worker;
    }

    public void setWorker(final String worker) {
        this.worker = worker;
    }

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
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("jobs")
    @Size(min = 2, max = 10000)
    public List<ArrayJobItem> getJobs() {
        return jobs;
    }

    public void setJobs(final List<ArrayJobItem> jobs) {
        this.jobs = jobs;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ArrayJobArgs arrayJobArgs = (ArrayJobArgs) o;
        return Objects.equals(service, arrayJobArgs.service) &&
                Objects.equals(worker, arrayJobArgs.worker) &&
                Objects.equals(portfolioVersion, arrayJobArgs.portfolioVersion) &&
                Objects.equals(jobs, arrayJobArgs.jobs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, worker, portfolioVersion, jobs);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class ArrayJobArgs {\n");

        sb.append("    service: ").append(toIndentedString(service)).append("\n");
        sb.append("    worker: ").append(toIndentedString(worker)).append("\n");
        sb.append("    portfolioVersion: ").append(toIndentedString(portfolioVersion)).append("\n");
        sb.append("    jobs: ").append(toIndentedString(jobs)).append("\n");
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

