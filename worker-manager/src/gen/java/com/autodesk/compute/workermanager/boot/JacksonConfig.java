package com.autodesk.compute.workermanager.boot;

import com.autodesk.compute.RFC3339DateFormat;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;

@Provider
public class JacksonConfig implements ContextResolver<ObjectMapper> {
    private final ObjectMapper objectMapper;

    public JacksonConfig() throws Exception {

        objectMapper = new ObjectMapper()
            .setDateFormat(new RFC3339DateFormat())
            .registerModule(new JodaModule() {
                {
                    addSerializer(DateTime.class, new StdSerializer<>(DateTime.class) {
                        @Override
                        public void serialize(final DateTime value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException, JsonGenerationException {
                            jgen.writeString(ISODateTimeFormat.dateTimeNoMillis().print(value));
                        }
                    });
                    addSerializer(LocalDate.class, new StdSerializer<>(LocalDate.class) {
                        @Override
                        public void serialize(final LocalDate value, final JsonGenerator jgen, final SerializerProvider provider) throws IOException, JsonGenerationException {
                            jgen.writeString(ISODateTimeFormat.date().print(value));
                        }
                    });

                }
            });
    }

    @Override
    public ObjectMapper getContext(final Class<?> arg0) {
        return objectMapper;
    }
}