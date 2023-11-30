package com.autodesk.compute.auth;

import com.autodesk.compute.auth.basic.BasicCredentials;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.Charsets;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class TestBasicCredentials {

    @Test
    public void emptyAuthHeader() {
        Assert.assertTrue(BasicCredentials.fromHeader(null).isEmpty());
        Assert.assertTrue(BasicCredentials.fromHeader("").isEmpty());
    }

    @Test
    public void notBasicAuthHeader() {
        Assert.assertTrue(BasicCredentials.fromHeader("Bearer mumblemumble").isEmpty());
    }

    @Test
    public void notBase64() {
        Assert.assertTrue(BasicCredentials.fromHeader("Basic &$@*#&(*%").isEmpty());
    }

    @Test
    public void urlAndStandardBase64BothWork() {
        final String clearText = "user:userCredential";
        final String urlBase64 = Base64.encodeBase64URLSafeString(clearText.getBytes(StandardCharsets.UTF_8));
        final String standardBase64 = Base64.encodeBase64String(clearText.getBytes(StandardCharsets.UTF_8));

        Assert.assertNotEquals("URL and regular encoding should differ", urlBase64, standardBase64);
        final Optional<BasicCredentials> urlCredentials = BasicCredentials.fromHeader("Basic " + urlBase64);
        final Optional<BasicCredentials> standardCredentials = BasicCredentials.fromHeader("Basic " + standardBase64);
        Assert.assertTrue(urlCredentials.isPresent());
        Assert.assertTrue(standardCredentials.isPresent());
        Assert.assertEquals("Both URL and Standard base64 encoding should work properly",
                urlCredentials.get(), standardCredentials.get());
    }
}
