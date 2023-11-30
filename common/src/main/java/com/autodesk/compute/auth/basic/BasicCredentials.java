package com.autodesk.compute.auth.basic;

import com.autodesk.compute.common.ComputeStringOps;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;

import java.util.Optional;

@AllArgsConstructor
@Data
@Slf4j
public class BasicCredentials {
    public static final String BASIC_AUTH_SCHEME = "Basic";

    private String userName;
    private String password;

    public static Optional<BasicCredentials> fromHeader(final String authHeader) {
        // A basic auth header is:
        // Authorization: Basic base64-encoded-stuff
        // where the base64-encoded stuff decodes to
        // username:password
        // Note: the apache base64 decode library silently handles both url-safe and standard encoding.
        if (ComputeStringOps.isNullOrEmpty(authHeader)) {
            log.info("Authorization header is empty");
            return Optional.empty();
        }
        if (!authHeader.startsWith(BASIC_AUTH_SCHEME + " ")) {
            log.info("Authorization header does not start with 'Basic '");
            return Optional.empty();
        }
        final String credential = authHeader.substring(BASIC_AUTH_SCHEME.length() + 1);
        try {
            if (!Base64.isBase64(credential)) {
                log.warn("Authorization header is not valid base64");
                return Optional.empty();
            }
            final String combinedValue = new String(Base64.decodeBase64(credential));
            return extractCredentialsFromDecodedHeader(combinedValue);
        } catch (final IllegalStateException e) {
            log.warn("Authorization header got exception during base64 decode", e);
            return Optional.empty();
        }
    }

    private static Optional<BasicCredentials> extractCredentialsFromDecodedHeader(final String combinedValue) {
        final int passwordPos = combinedValue.indexOf(':');
        if (passwordPos <= 0 || passwordPos == combinedValue.length()) {
            log.warn("Authorization header is missing a colon");
            return Optional.empty();
        }
        return Optional.of(
                new BasicCredentials(
                        combinedValue.substring(0, passwordPos), combinedValue.substring(passwordPos + 1)));
    }
}
