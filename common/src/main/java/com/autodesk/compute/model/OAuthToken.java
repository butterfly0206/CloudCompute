package com.autodesk.compute.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;
import lombok.With;


@Value
@Builder
@JsonDeserialize(builder = OAuthToken.OAuthTokenBuilder.class)
public class OAuthToken {

    @JsonProperty("scope")
    private String scope;;
    @JsonProperty("expires_in")
    private Integer expiresIn;
    @JsonProperty("client_id")
    private String clientId;
    @JsonProperty("access_token")
    private AccessToken accessToken;
    @With
    private String appId;
    @With
    private String bearerToken;
    @With
    private String apigeeSecret;

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown=true, value = { "appId", "bearerToken", "apigeeSecret" })
    public static class OAuthTokenBuilder{}

}
