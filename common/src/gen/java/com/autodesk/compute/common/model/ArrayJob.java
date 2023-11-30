package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class ArrayJob {

    private String service;
    private String worker;
    private String portfolioVersion;
    private String jobID;
    private List<JobInfo> jobs = new ArrayList<>();

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
     * Parent job ID assigned to all the jobs in the array
     **/

    @ApiModelProperty(value = "Parent job ID assigned to all the jobs in the array")
    @JsonProperty("jobID")
    @Pattern(regexp = "[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}(:array(:(0|([1-9]\\d{0,3})))?)?")
    public String getJobID() {
        return jobID;
    }

    public void setJobID(final String jobID) {
        this.jobID = jobID;
    }

    /**
     * Array all the jobs crated
     **/

    @ApiModelProperty(value = "Array all the jobs crated")
    @JsonProperty("jobs")
    public List<JobInfo> getJobs() {
        return jobs;
    }

    public void setJobs(final List<JobInfo> jobs) {
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
        final ArrayJob arrayJob = (ArrayJob) o;
        return Objects.equals(service, arrayJob.service) &&
                Objects.equals(worker, arrayJob.worker) &&
                Objects.equals(portfolioVersion, arrayJob.portfolioVersion) &&
                Objects.equals(jobID, arrayJob.jobID) &&
                Objects.equals(jobs, arrayJob.jobs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, worker, portfolioVersion, jobID, jobs);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class ArrayJob {\n");

        sb.append("    service: ").append(toIndentedString(service)).append("\n");
        sb.append("    worker: ").append(toIndentedString(worker)).append("\n");
        sb.append("    portfolioVersion: ").append(toIndentedString(portfolioVersion)).append("\n");
        sb.append("    jobID: ").append(toIndentedString(jobID)).append("\n");
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

