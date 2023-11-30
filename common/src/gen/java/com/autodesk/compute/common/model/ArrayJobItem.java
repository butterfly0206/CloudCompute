package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class ArrayJobItem {

    private List<String> tags = new ArrayList<>();
    private Object payload;

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

    @ApiModelProperty(example = "{\"inputFilePath\": \"https://developer-dev.api.autodesk.com/oss/v2/buckets/longstestbucket1234/objects/mytestfile01.txt\"}", required = true, value = "Arbitrary Json data, conforming to a schema associated with the application definition")
    @JsonProperty("payload")
    @NotNull
    public Object getPayload() {
        return payload;
    }

    public void setPayload(final Object payload) {
        this.payload = payload;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ArrayJobItem arrayJobItem = (ArrayJobItem) o;
        return Objects.equals(tags, arrayJobItem.tags) &&
                Objects.equals(payload, arrayJobItem.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags, payload);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class ArrayJobItem {\n");

        sb.append("    tags: ").append(toIndentedString(tags)).append("\n");
        sb.append("    payload: ").append(toIndentedString(payload)).append("\n");
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

