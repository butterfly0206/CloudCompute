package com.autodesk.compute.common;

import com.autodesk.compute.configuration.ComputeErrorCodes;
import com.autodesk.compute.configuration.ComputeException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@UtilityClass
public class Json { //NOPMD
    /*
     * - Use this object mapper globally for parsing JSON.
     */
    public final ObjectMapper mapper = makeMapper();

    private ObjectMapper makeMapper() {
        final var jf = new JsonFactory();
        jf.enable(JsonParser.Feature.ALLOW_COMMENTS);
        return new ObjectMapper(jf);
    }

    /*
        Returns a json string representation of the object
     */
    @NonNull
    @SneakyThrows(JsonProcessingException.class)
    public String toJsonString(final Object object) {
        return Json.mapper.writeValueAsString(object);
    }

    /*
    Returns a json string representation of the object
 */
    @NonNull
    public Map fromJsonStringToMap(final String string) throws ComputeException {
        try {
            return Json.mapper.readValue(string, Map.class);
        } catch (final JsonProcessingException e) {
            throw new ComputeException(ComputeErrorCodes.SERVER_UNEXPECTED, "Provided string wasn't a map; exception suppressed due to possible secrets in map");
        }
    }
}
