package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class Job {

    private String type = "Job";
    private String service;
    private String worker;
    private String portfolioVersion;
    private String jobID;
    private Status status;
    private JobProgress progress;
    private String serviceClientId;
    private String creationTime;
    private String modificationTime;
    private String tagsModificationTime;
    private List<Failure> errors = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private Object payload;
    private Object result;
    private List<StatusUpdate> statusUpdates = new ArrayList<>();

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("type")
    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

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
    @JsonProperty("status")
    public Status getStatus() {
        return status;
    }

    public void setStatus(final Status status) {
        this.status = status;
    }

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("progress")
    public JobProgress getProgress() {
        return progress;
    }

    public void setProgress(final JobProgress progress) {
        this.progress = progress;
    }

    /**
     * service.clientId used as a search key for recent/archived jobs, this value is created internally using the Service plus the authorized client ID
     **/

    @ApiModelProperty(example = "fpccomp-c-uw2.KAMAJwfMQEfgdiQ...", value = "service.clientId used as a search key for recent/archived jobs, this value is created internally using the Service plus the authorized client ID ")
    @JsonProperty("serviceClientId")
    public String getServiceClientId() {
        return serviceClientId;
    }

    public void setServiceClientId(final String serviceClientId) {
        this.serviceClientId = serviceClientId;
    }

    /**
     * Indicates the time when the job record was created in ISO8601 format
     **/

    @ApiModelProperty(example = "2020-02-20T23:20:07.206Z", value = "Indicates the time when the job record was created in ISO8601 format ")
    @JsonProperty("creationTime")
    public String getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(final String creationTime) {
        this.creationTime = creationTime;
    }

    /**
     * Indicates the time of the job record&#39;s last modification, expressed in milliseconds since midnight January 1, 1970.
     **/

    @ApiModelProperty(example = "1548358601200", value = "Indicates the time of the job record's last modification, expressed in milliseconds since midnight January 1, 1970. ")
    @JsonProperty("modificationTime")
    public String getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(final String modificationTime) {
        this.modificationTime = modificationTime;
    }

    /**
     * Indicates the time of the job tags&#39;s last modification, expressed in milliseconds since midnight January 1, 1970.
     **/

    @ApiModelProperty(example = "1548358601200", value = "Indicates the time of the job tags's last modification, expressed in milliseconds since midnight January 1, 1970. ")
    @JsonProperty("tagsModificationTime")
    public String getTagsModificationTime() {
        return tagsModificationTime;
    }

    public void setTagsModificationTime(final String tagsModificationTime) {
        this.tagsModificationTime = tagsModificationTime;
    }

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("errors")
    public List<Failure> getErrors() {
        return errors;
    }

    public void setErrors(final List<Failure> errors) {
        this.errors = errors;
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
     * Arbitrary Json data, conforming to a schema associated with the application definition
     **/

    @ApiModelProperty(example = "{\"outputFilePath\": \"https://developer-dev.api.autodesk.com/oss/v2/buckets/longstestbucket1234/objects/mytestfile02.txt\"}", value = "Arbitrary Json data, conforming to a schema associated with the application definition")
    @JsonProperty("result")
    public Object getResult() {
        return result;
    }

    public void setResult(final Object result) {
        this.result = result;
    }

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("statusUpdates")
    public List<StatusUpdate> getStatusUpdates() {
        return statusUpdates;
    }

    public void setStatusUpdates(final List<StatusUpdate> statusUpdates) {
        this.statusUpdates = statusUpdates;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Job job = (Job) o;
        return Objects.equals(type, job.type) &&
                Objects.equals(service, job.service) &&
                Objects.equals(worker, job.worker) &&
                Objects.equals(portfolioVersion, job.portfolioVersion) &&
                Objects.equals(jobID, job.jobID) &&
                Objects.equals(status, job.status) &&
                Objects.equals(progress, job.progress) &&
                Objects.equals(serviceClientId, job.serviceClientId) &&
                Objects.equals(creationTime, job.creationTime) &&
                Objects.equals(modificationTime, job.modificationTime) &&
                Objects.equals(tagsModificationTime, job.tagsModificationTime) &&
                Objects.equals(errors, job.errors) &&
                Objects.equals(tags, job.tags) &&
                Objects.equals(payload, job.payload) &&
                Objects.equals(result, job.result) &&
                Objects.equals(statusUpdates, job.statusUpdates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, worker, portfolioVersion, jobID, status, progress, serviceClientId, creationTime, modificationTime, tagsModificationTime, errors, tags, payload, result, statusUpdates);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class Job {\n");

        sb.append("    type: ").append(toIndentedString(type)).append("\n");
        sb.append("    service: ").append(toIndentedString(service)).append("\n");
        sb.append("    worker: ").append(toIndentedString(worker)).append("\n");
        sb.append("    portfolioVersion: ").append(toIndentedString(portfolioVersion)).append("\n");
        sb.append("    jobID: ").append(toIndentedString(jobID)).append("\n");
        sb.append("    status: ").append(toIndentedString(status)).append("\n");
        sb.append("    progress: ").append(toIndentedString(progress)).append("\n");
        sb.append("    serviceClientId: ").append(toIndentedString("suppressed")).append("\n");
        sb.append("    creationTime: ").append(toIndentedString(creationTime)).append("\n");
        sb.append("    modificationTime: ").append(toIndentedString(modificationTime)).append("\n");
        sb.append("    tagsModificationTime: ").append(toIndentedString(tagsModificationTime)).append("\n");
        sb.append("    errors: ").append(toIndentedString(errors)).append("\n");
        sb.append("    tags: ").append(toIndentedString(tags)).append("\n");
        sb.append("    payload: ").append(toIndentedString(payload)).append("\n");
        sb.append("    result: ").append(toIndentedString(result)).append("\n");
        sb.append("    statusUpdates: ").append(toIndentedString(statusUpdates)).append("\n");
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

