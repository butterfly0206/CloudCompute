package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

import java.util.Objects;


public class WorkerIdentity {

    private String workerIdentity;

    /**
     *
     **/

    @ApiModelProperty(example = "XYZ", value = "")
    @JsonProperty("workerIdentity")
    public String getWorkerIdentity() {
        return workerIdentity;
    }

    public void setWorkerIdentity(final String workerIdentity) {
        this.workerIdentity = workerIdentity;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WorkerIdentity workerIdentity = (WorkerIdentity) o;
        return Objects.equals(workerIdentity, workerIdentity.workerIdentity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workerIdentity);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class WorkerIdentity {\n");

        sb.append("    workerIdentity: ").append(toIndentedString(workerIdentity)).append("\n");
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

