package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class Error {

    private String code;
    private String description;
    private String message;
    private Object details;
    private Object requestContext;
    private String stackTrace;

    /**
     *
     **/

    @ApiModelProperty(required = true, value = "")
    @JsonProperty("code")
    @NotNull
    public String getCode() {
        return code;
    }

    public void setCode(final String code) {
        this.code = code;
    }

    /**
     *
     **/

    @ApiModelProperty(required = true, value = "")
    @JsonProperty("description")
    @NotNull
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     *
     **/

    @ApiModelProperty(required = true, value = "")
    @JsonProperty("message")
    @NotNull
    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("details")
    public Object getDetails() {
        return details;
    }

    public void setDetails(final Object details) {
        this.details = details;
    }

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("requestContext")
    public Object getRequestContext() {
        return requestContext;
    }

    public void setRequestContext(final Object requestContext) {
        this.requestContext = requestContext;
    }

    /**
     *
     **/

    @ApiModelProperty(value = "")
    @JsonProperty("stackTrace")
    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(final String stackTrace) {
        this.stackTrace = stackTrace;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Error error = (Error) o;
        return Objects.equals(code, error.code) &&
                Objects.equals(description, error.description) &&
                Objects.equals(message, error.message) &&
                Objects.equals(details, error.details) &&
                Objects.equals(requestContext, error.requestContext) &&
                Objects.equals(stackTrace, error.stackTrace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, description, message, details, requestContext, stackTrace);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class Error {\n");

        sb.append("    code: ").append(toIndentedString(code)).append("\n");
        sb.append("    description: ").append(toIndentedString(description)).append("\n");
        sb.append("    message: ").append(toIndentedString(message)).append("\n");
        sb.append("    details: ").append(toIndentedString(details)).append("\n");
        sb.append("    requestContext: ").append(toIndentedString(requestContext)).append("\n");
        sb.append("    stackTrace: ").append(toIndentedString(stackTrace)).append("\n");
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

