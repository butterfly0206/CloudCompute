package com.autodesk.compute.common;

import com.autodesk.compute.configuration.ComputeException;
import com.autodesk.compute.test.categories.UnitTests;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

@Category(UnitTests.class)
@Slf4j
public class TestComputeStringOps {

    @Test
    public void testMakeString() {
        final String expect = "foobarbang100null";
        assertEquals(expect, ComputeStringOps.makeString("foo", "bar", "bang", 100, null));
    }

    @Test
    public void testMakeSearchIndex() {
        final String expect = "foo";
        assertEquals(expect, ComputeStringOps.makeServiceClientId("foo", "bar", "bang"));
        final String expect2 = "bar";
        assertEquals(expect2, ComputeStringOps.makeServiceClientId(null, "bar", "bang"));
        final String expect3 = "bang";
        assertEquals(expect3, ComputeStringOps.makeServiceClientId(null, null, "bang"));
    }

    @Test
    public void testMakeNextToken() {
        final String expect = "Zm9vfGJhcnxiYW5nfDEwMHw=";
        assertEquals(expect, ComputeStringOps.makeNextToken("foo", "bar", "bang", 100, null));
        final String expect2 = "Zm9vfGJhcnx8MTAwfA==";
        assertEquals(expect2, ComputeStringOps.makeNextToken("foo", "bar", null, 100, null));
    }

    @Test
    public void testMakeAndParseNextToken() throws ComputeException {
        final List<String> expect = new ArrayList<>();
        expect.add("foo");
        expect.add("bar");
        expect.add("bang");
        expect.add("100");
        expect.add("");
        assertEquals(expect, ComputeStringOps.parseNextToken("Zm9vfGJhcnxiYW5nfDEwMHw="));
        assertEquals(expect, ComputeStringOps.parseNextToken(ComputeStringOps.makeNextToken("foo", "bar", "bang", 100, null)));
        final List<String> expect2 = new ArrayList<>();
        expect2.add("foo");
        expect2.add("bar");
        expect2.add("");
        expect2.add("100");
        expect2.add("");
        assertEquals(expect2, ComputeStringOps.parseNextToken("Zm9vfGJhcnx8MTAwfA=="));
        assertEquals(expect2, ComputeStringOps.parseNextToken(ComputeStringOps.makeNextToken("foo", "bar", null, 100, null)));
    }

    @Test
    public void testCompressString() throws ComputeException {
        final String uncompressedString = "myString";
        final ByteBuffer compressedString = ComputeStringOps.compressString(uncompressedString);
        assertEquals("Compressed string must be the same when uncompressed", uncompressedString, ComputeStringOps.uncompressString(compressedString));
    }

    @Test
    public void testCompressLongString() throws ComputeException {
        final StringBuilder sb = new StringBuilder();
        IntStream.range(0, 1_000).forEach(i -> sb.append(i));
        final String testString = sb.toString();
        final ByteBuffer compressedString = ComputeStringOps.compressString(testString);
        assertEquals("Compressed string must be the same when uncompressed", testString, ComputeStringOps.uncompressString(compressedString));
    }
}
