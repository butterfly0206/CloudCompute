package com.autodesk.compute.auth.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * - token class used for vault-java-driver's Auth object
 *
 * @author mokl
 */
@Value
@Builder
@AllArgsConstructor
public class VaultToken {
    private final String id;
    @Singular
    private List<String> policies;
    @Singular("meta")
    private Map<String, String> meta;
    private Boolean noParent;
    private Boolean noDefaultPolicy;
    private Long ttl;
    private String displayName;
    private Long numUses;
}
