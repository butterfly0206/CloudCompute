package com.autodesk.compute.auth;

import com.autodesk.compute.auth.oauth.OxygenSecurityContext;
import com.autodesk.compute.model.AccessToken;
import com.autodesk.compute.model.OAuthToken;
import com.autodesk.compute.test.categories.UnitTests;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@Category(UnitTests.class)
@RunWith(Parameterized.class)
@AllArgsConstructor
public class OxygenSecurityContextTests {

    static OxygenSecurityContextTestCase.OxygenSecurityContextTestCaseBuilder builder = OxygenSecurityContextTestCase.builder();
    static OAuthToken.OAuthTokenBuilder tokenBuilder = OAuthToken.builder();
    static AccessToken.AccessTokenBuilder atBuilder = AccessToken.builder();

    private static final String TEST_GATEWAY_SECRET_V1 = "testGatewaySecretV1";
    private static final String TEST_GATEWAY_SECRET_V2 = "testGatewaySecretV2";

    static private final Set<String> APIGEE_SECRETS = ImmutableSet.of(TEST_GATEWAY_SECRET_V1, TEST_GATEWAY_SECRET_V2);
    static private final String MOCK_BEARER_TOKEN = "mockBearerToken";
    static private final String MOCK_APPID = "mockAppId";
    static private final String MOCK_CLIENTID = "mockClientId";
    static private final String MOCK_USERID = "mockUserId";

    private final OxygenSecurityContextTestCase testData;

    @Parameterized.Parameters(name = "{index}: {0}")
    public static Iterable<OxygenSecurityContextTestCase> data() {
        return
          Stream.of(
            Stream.of(
              OxygenSecurityContextTestCase.of("Null token no enforcement", true, null, false, null),
              OxygenSecurityContextTestCase.of("Null token with enforcement", false, null, true, null),
              OxygenSecurityContextTestCase.of("Scopes separated by spaces", true, tokenBuilder.scope("data:read data:write").build(), false,
                      Lists.newArrayList("data:write", "data:read")),
              OxygenSecurityContextTestCase.of("Scopes separated by commas", true, tokenBuilder.scope("data:read,data:write").build(), false,
                      Lists.newArrayList("data:write", "data:read")),
              OxygenSecurityContextTestCase.of("Null client_id in access token", true,
                      tokenBuilder.clientId(MOCK_CLIENTID).appId(MOCK_APPID).apigeeSecret(TEST_GATEWAY_SECRET_V1).accessToken(
                              atBuilder.clientId(null).email("foo@bar.com").entitlements("ENTITLEMENTS").userId(MOCK_USERID).build()
                      ).build(), true, null),
              OxygenSecurityContextTestCase.of("Null client_id in OAuthToken", false,
                      tokenBuilder.clientId(null).appId(MOCK_APPID).apigeeSecret(TEST_GATEWAY_SECRET_V2).accessToken(
                              atBuilder.clientId(null).email("foo@bar.com").entitlements("ENTITLEMENTS").userId(MOCK_USERID).build()
                      ).build(), true, null)

            )
          ).flatMap(Function.identity()).collect(Collectors.toList());
    }

    @Test
    public void testOneCase() throws IOException {
        final OxygenSecurityContext context = new OxygenSecurityContext(
                APIGEE_SECRETS,
                testData.isEnforceApigee(),
                testData.getTokenData());
        final boolean isSecure = context.isSecure();
        assertEquals(String.format("Test case %s failed", testData.toString()), testData.isShouldBeSecure(), isSecure);

        final List<String> scopes = MoreObjects.firstNonNull(testData.getScopes(), Collections.emptyList());
        for (final String scope : scopes) {
            assertTrue(String.format("Scope %s should be present, but isn't", scope), context.isUserInRole(scope));
        }
    }
}
