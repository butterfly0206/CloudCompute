package com.autodesk.compute.exception;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.MoreObjects;
import org.slf4j.MDC;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

public interface IComputeException {
    ComputeErrorCodes getCode();

    JsonNode getDetails();

    Throwable getCause();

    String getMessage();

    // Derived from: https://gist.github.com/esfand/3c9fe2f6ef30bbf80949
    default void setDetailsField(final JsonNode deserializedDetails, final Class<?> clazz) {
        try {
            // use getDeclaredField as the field is non public
            final Field detailsField = clazz.getDeclaredField("details");

            // make the field non final
            detailsField.setAccessible(true);
            detailsField.set(this, deserializedDetails);
            // make the field final again
            detailsField.setAccessible(false);
        } catch (final Exception e) {
            org.slf4j.LoggerFactory.getLogger(IComputeException.class).warn("unable to set the exception details after deserialization", e);
        }
    }

    default JsonNode makeDetails(final JsonNode existingDetails) {
        if (existingDetails == null)
            return getMDC();
        if (existingDetails.isObject())
            ((ObjectNode) existingDetails).set("MDC", getMDC());
        return existingDetails;
    }

    default ObjectNode getMDC() {
        final Map<String, String> context = MoreObjects.firstNonNull(MDC.getCopyOfContextMap(), Collections.emptyMap());

        final ObjectMapper mapper = new ObjectMapper();
        return mapper.valueToTree(context);
    }

}
