package com.autodesk.compute.auth;

import com.autodesk.compute.model.OAuthToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor(staticName="of")
public class OxygenSecurityContextTestCase {
    private String description;
    private boolean shouldBeSecure;
    private OAuthToken tokenData;
    private boolean enforceApigee;
    private List<String> scopes;
}
