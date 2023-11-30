package com.autodesk.compute.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ApiModel(description = "Data returned from a search ")
@jakarta.annotation.Generated(value = "org.openapitools.codegen.languages.JavaResteasyServerCodegen")
public class SearchResult {

    private List<Job> jobs = new ArrayList<>();
    private String lastUpdateTime;
    private String nextToken;

    /**
     *
     **/

    @ApiModelProperty(required = true, value = "")
    @JsonProperty("jobs")
    @NotNull
    public List<Job> getJobs() {
        return jobs;
    }

    public void setJobs(final List<Job> jobs) {
        this.jobs = jobs;
    }

    /**
     * Time from the last database update. Use it as the \&quot;fromTime\&quot; in the next search. Expressed in milliseconds since midnight January 1, 1970.
     **/

    @ApiModelProperty(example = "1562599327", required = true, value = "Time from the last database update. Use it as the \"fromTime\" in the next search. Expressed in milliseconds since midnight January 1, 1970. ")
    @JsonProperty("lastUpdateTime")
    @NotNull
    public String getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(final String lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    /**
     * Internal token used for search pagination, returned in search results for queries which span multiple pages
     **/

    @ApiModelProperty(example = "Y2VlNjkwYzItNThiYy00YzE0LThiMzktMGFhNGNjNDQ0NTQ3OmZwY2NvbXAtYy11dzItc2IuS0FNQUpualhKV3ZqaXNaY250ZG1vRGYzeEdKT2VEcVU6MTU0ODQ1MTY3NzcwMg", required = true, value = "Internal token used for search pagination, returned in search results for queries which span multiple pages ")
    @JsonProperty("nextToken")
    @NotNull
    @Pattern(regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
    public String getNextToken() {
        return nextToken;
    }

    public void setNextToken(final String nextToken) {
        this.nextToken = nextToken;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SearchResult searchResult = (SearchResult) o;
        return Objects.equals(jobs, searchResult.jobs) &&
                Objects.equals(lastUpdateTime, searchResult.lastUpdateTime) &&
                Objects.equals(nextToken, searchResult.nextToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobs, lastUpdateTime, nextToken);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("class SearchResult {\n");

        sb.append("    jobs: ").append(toIndentedString(jobs)).append("\n");
        sb.append("    lastUpdateTime: ").append(toIndentedString(lastUpdateTime)).append("\n");
        sb.append("    nextToken: ").append(toIndentedString(nextToken)).append("\n");
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

