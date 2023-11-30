package com.autodesk.compute.common;

import com.autodesk.compute.model.OAuthToken;
import jakarta.ws.rs.core.MultivaluedMap;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

@UtilityClass
public class OAuthTokenUtils {
    private static final Pattern splitRegex = Pattern.compile("[,\\s]+");

    public boolean hasScope(final OAuthToken token, final String requestedScope) {
        if (ComputeStringOps.isNullOrEmpty(requestedScope))
            return false;
        final String[] allScopes = splitRegex.split(token.getScope());
        return Arrays.stream(allScopes).anyMatch(str -> str.equals(requestedScope));
    }

    public OAuthToken from(final MultivaluedMap<String, String> headers) throws IOException {
        String tokenDataStr = headers.getFirst("x-ads-token-data");
        if (ComputeStringOps.isNullOrEmpty(tokenDataStr))
            tokenDataStr = "{}";
        OAuthToken tokenData = Json.mapper.readValue(tokenDataStr, OAuthToken.class);
        final String appId = headers.getFirst("x-ads-appid");
        if (!ComputeStringOps.isNullOrEmpty(appId))
            tokenData = tokenData.withAppId(appId);
        final String authHeader = headers.getFirst("Authorization");
        if (!ComputeStringOps.isNullOrEmpty(authHeader))
            tokenData = tokenData.withBearerToken(authHeader.replace("Bearer ", ""));
        final String apigeeHeader = headers.getFirst("x-ads-gateway-secret");
        if (!ComputeStringOps.isNullOrEmpty(apigeeHeader))
            tokenData = tokenData.withApigeeSecret(apigeeHeader);
        return tokenData;
    }
}
