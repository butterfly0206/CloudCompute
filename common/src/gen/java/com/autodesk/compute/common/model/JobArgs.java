package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class JobArgs {

    private String service;
    private String worker;
    private String portfolioVersion;
    private List<String> tags = new ArrayList<>();
    private Object payload;
    private String idempotencyId;
    private String userType;

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

    @ApiModelProperty(example = "[\"simulation\",\"bicycles\"]", value = "")
    @JsonProperty("tags")
    public List<String> getTags() {
        return tags;
    }

    public void setTags(final List<String> tags) {
        this.tags = tags;
    }

    /**
     * Arbitrary Json data, conforming to a schema associated with the application definition
     **/

    @ApiModelProperty(example = "{\"inputFilePath\": \"https://developer-dev.api.autodesk.com/oss/v2/buckets/longstestbucket1234/objects/mytestfile01.txt\"}", value = "Arbitrary Json data, conforming to a schema associated with the application definition")
    @JsonProperty("payload")
    public Object getPayload() {
        return payload;
    }

    public void setPayload(final Object payload) {
        this.payload = payload;
    }

    /**
     * Calls with the same idempoyencyId are idempotent; if job with the same idempotencyId exists, the second job will be rejected.
     **/

    @ApiModelProperty(example = "b67d50dc-ce3c-4dad-acb2-1d34ca536ac3", value = "Calls with the same idempoyencyId are idempotent; if job with the same idempotencyId exists, the second job will be rejected.")
    @JsonProperty("idempotencyId")
    @Size(min = 8, max = 128)
    public String getIdempotencyId() {
        return idempotencyId;
    }

    public void setIdempotencyId(final String idempotencyId) {
        this.idempotencyId = idempotencyId;
    }

    /**
     * One of the user types defined in jobConcurrencyLimits part of ComputeSpecification
     **/

    @ApiModelProperty(example = "Student", value = "One of the user types defined in jobConcurrencyLimits part of ComputeSpecification")
    @JsonProperty("userType")
    public String getUserType() {
        return userType;
    }

    public void setUserType(final String userType) {
        this.userType = userType;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JobArgs jobArgs = (JobArgs) o;
        return Objects.equals(service, jobArgs.service) &&
                Objects.equals(worker, jobArgs.worker) &&
                Objects.equals(portfolioVersion, jobArgs.portfolioVersion) &&
                Objects.equals(tags, jobArgs.tags) &&
                Objects.equals(payload, jobArgs.payload) &&
                Objects.equals(idempotencyId, jobArgs.idempotencyId) &&
                Objects.equals(userType, jobArgs.userType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, worker, portfolioVersion, tags, payload, idempotencyId, userType);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class JobArgs {\n");

        sb.append("    service: ").append(toIndentedString(service)).append("\n");
        sb.append("    worker: ").append(toIndentedString(worker)).append("\n");
        sb.append("    portfolioVersion: ").append(toIndentedString(portfolioVersion)).append("\n");
        sb.append("    tags: ").append(toIndentedString(tags)).append("\n");
        sb.append("    payload: ").append(toIndentedString(payload)).append("\n");
        sb.append("    idempotencyId: ").append(toIndentedString(idempotencyId)).append("\n");
        sb.append("    userType: ").append(toIndentedString(userType)).append("\n");
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

