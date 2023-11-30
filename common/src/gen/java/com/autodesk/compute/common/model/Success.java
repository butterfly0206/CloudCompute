package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class Success {

    private Object result;

    /**
     *
     **/

    @ApiModelProperty(example = "{\"outputFilePath\":\"https://developer-dev.api.autodesk.com/oss/v2/buckets/longstestbucket1234/objects/mytestfile02.txt\"}", value = "")
    @JsonProperty("result")
    public Object getResult() {
        return result;
    }

    public void setResult(final Object result) {
        this.result = result;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Success success = (Success) o;
        return Objects.equals(result, success.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class Success {\n");

        sb.append("    result: ").append(toIndentedString(result)).append("\n");
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

