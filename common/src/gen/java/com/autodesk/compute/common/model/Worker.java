package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class Worker {

    private String service;
    private String worker;
    private String portfolioVersion;

    /**
     * An appdef moniker
     **/

    @ApiModelProperty(example = "amktool-c-uw2", required = true, value = "An appdef moniker")
    @JsonProperty("service")
    @NotNull
    public String getService() {
        return service;
    }

    public void setService(String service) {
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

    public void setWorker(String worker) {
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

    public void setPortfolioVersion(String portfolioVersion) {
        this.portfolioVersion = portfolioVersion;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Worker worker = (Worker) o;
        return Objects.equals(service, worker.service) &&
                Objects.equals(worker, worker.worker) &&
                Objects.equals(portfolioVersion, worker.portfolioVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, worker, portfolioVersion);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Worker {\n");

        sb.append("    service: ").append(toIndentedString(service)).append("\n");
        sb.append("    worker: ").append(toIndentedString(worker)).append("\n");
        sb.append("    portfolioVersion: ").append(toIndentedString(portfolioVersion)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

