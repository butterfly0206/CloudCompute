package com.autodesk.compute.auth;

import com.autodesk.compute.common.Json;
import com.autodesk.compute.model.OAuthToken;
import com.autodesk.compute.test.categories.UnitTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Category(UnitTests.class)
public class OAuthTokenTests {

    @Test
    public void parseTwoLeggedOAuthToken() throws IOException {
        String twoLeggedToken =  "{\"scope\":\"\",\"expires_in\":3600,\"client_id\":\"<your client key>\",\"access_token\":{\"client_id\":\"<your client key>\"}}";
        OAuthToken parsedToken = Json.mapper.readValue(twoLeggedToken, OAuthToken.class);
        assertNotNull(parsedToken);
        assertEquals(3600, parsedToken.getExpiresIn().intValue());
    }

    @Test
    public void parseThreeLeggedOAuthToken() throws IOException {
        String threeLeggedToken = "{\"scope\":\"\",\"expires_in\":3600,\"client_id\":\"<your client key>\",\"access_token\":{\"username\":\"alanedwardes\",\"email\":\"alan.edwardes@autodesk.com\",\"userid\":\"X9D3NYWZAFVP\",\"lastname\":\"Edwardes\",\"firstname\":\"Alan\",\"entitlements\":\"<urns defining user entitlements>\"}}";
        OAuthToken parsedToken = Json.mapper.readValue(threeLeggedToken, OAuthToken.class);
        assertNotNull(parsedToken);
        assertEquals("", parsedToken.getScope());
    }

    @Test
    public void parseTokensWithUnknownFields() throws IOException {
        String threeLeggedToken = "{\"scope\":\"data:write\",\"mumble\":\"frotz\",\"expires_in\":3600,\"client_id\":\"<your client key>\",\"access_token\":{\"username\":\"alanedwardes\",\"email\":\"alan.edwardes@autodesk.com\",\"userid\":\"X9D3NYWZAFVP\",\"lastname\":\"Edwardes\",\"firstname\":\"Alan\",\"entitlements\":\"<urns defining user entitlements>\"}}";
        OAuthToken parsedToken = Json.mapper.readValue(threeLeggedToken, OAuthToken.class);
        assertNotNull(parsedToken);
        assertEquals("Token should have parsed properly", "data:write", parsedToken.getScope());
    }
}

