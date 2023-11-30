package com.autodesk.compute.common;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.google.common.base.Strings;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@UtilityClass
public class ComputeStringOps {    //NOPMD

    public static boolean isNullOrEmpty(final String nullOrEmpty) {
        return nullOrEmpty == null || nullOrEmpty.trim().isEmpty();
    }

    public static String makeString(final Object... objects) {
        final StringBuilder sb = new StringBuilder();
        for (final Object obj : objects) {
            if (obj == null)
                sb.append("null");
            else
                sb.append(obj.toString());
        }
        return sb.toString();
    }

    public static String firstNonEmpty(final String... strings) {
        for (final String oneString : strings)
            if (!Strings.isNullOrEmpty(oneString))
                return oneString;
        return null;
    }

    public static String makeNextToken(final Object... objects) {
        final StringBuilder sb = new StringBuilder();
        String delim = "";
        for (final Object obj : objects) {
            sb.append(delim);
            if (obj != null) {
                sb.append(obj.toString());
            }
            delim = "|";
        }
        final String unencoded = sb.toString();
        final String encoded = Base64.getEncoder().encodeToString(unencoded.getBytes());
        log.debug(makeString("makeNextToken unencoded=", unencoded, ", encoded=", encoded));
        return encoded;
    }

    public static List<String> parseNextToken(final String encoded) throws ComputeException {
        if (encoded == null) {
            return new ArrayList<>();
        }
        try {
            final String unencoded = new String(Base64.getDecoder().decode(encoded));
            final List<String> parts = Arrays.asList(unencoded.split("\\|", -1));
            log.debug(makeString("parseNextToken encoded=", encoded, ", unencoded=", unencoded, ", parts=", parts));
            return parts;
        } catch (final IllegalArgumentException e) {
            log.error(makeString("Invalid next token specified: ", encoded));
            throw new ComputeException(ComputeErrorCodes.BAD_REQUEST, e);
        }
    }

    // Suppressing "Optional value should only be accessed after calling isPresent()"
    // because the @NonNull serviceId guarantees the get will always succeed
    @SuppressWarnings("squid:S3655")
    public static String makeServiceClientId(final String userId, final String clientId, @NonNull final String serviceId) {
        return Stream.of(userId, clientId, serviceId).filter(Objects::nonNull).findFirst().get();
    }

    @SneakyThrows(IOException.class)
    public static ByteBuffer compressString(final String input) {
        // Compress the UTF-8 encoded String into a byte[]
        final byte[] compressedBytes;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (final GZIPOutputStream os = new GZIPOutputStream(baos)) {
                os.write(input.getBytes(StandardCharsets.UTF_8));
            }
            compressedBytes = baos.toByteArray();
        }

        // The following code writes the compressed bytes to a ByteBuffer.
        // A simpler way to do this is by simply calling
        // ByteBuffer.wrap(compressedBytes);
        // However, the longer form below shows the importance of resetting the
        // position of the buffer
        // back to the beginning of the buffer if you are writing bytes directly
        // to it, since the SDK
        // will consider only the bytes after the current position when sending
        // data to DynamoDB.
        // Using the "wrap" method automatically resets the position to zero.
        final ByteBuffer buffer = ByteBuffer.allocate(compressedBytes.length);
        buffer.put(compressedBytes, 0, compressedBytes.length);
        buffer.position(0); // Important: reset the position of the ByteBuffer
        // to the beginning
        return buffer.asReadOnlyBuffer();
    }

    @SneakyThrows(IOException.class)
    public static String uncompressString(final ByteBuffer input) {
        input.position(0);      // Paranoia: reset the position to the start of the buffer
        final byte[] bytes = new byte[input.remaining()];
        input.get(bytes, 0, bytes.length);
        try (final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             final ByteArrayOutputStream baos = new ByteArrayOutputStream();
             final GZIPInputStream is = new GZIPInputStream(bais)) {
            final int chunkSize = 1024;
            final byte[] buffer = new byte[chunkSize];
            int length = 0;
            while ((length = is.read(buffer, 0, chunkSize)) != -1) {
                baos.write(buffer, 0, length);
            }

            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @SneakyThrows(ComputeException.class)
    public static void validateNonEmptyOrThrow(final String toTest, final String message) throws ComputeException {
        if (ComputeStringOps.isNullOrEmpty(toTest))
            throw new ComputeException(ComputeErrorCodes.BAD_REQUEST, message);
    }


}
