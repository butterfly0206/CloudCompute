package com.autodesk.compute.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

/*
 * ComputeResteasyJackson2Provider provides validation on REST path arguments,
 * extending functionality of ResteasyJackson2Provider
 * Ref: http://docs.jboss.org/resteasy/docs/3.1.4.Final/userguide/html/Validation.html
 * Ref: http://docs.jboss.org/hibernate/validator/6.0/reference/en-US/html_single/
 */

@Provider
@Consumes({"application/json", "application/*+json", "text/json"})
@Produces({"application/json", "application/*+json", "text/json"})
public class ComputeResteasyJackson2Provider extends ResteasyJackson2Provider
{
    public static class InputValidationException extends IOException {
        public InputValidationException(final String message) {
            super(message);
        }
    }


    @Override
    public Object readFrom(final Class<Object> type, final Type genericType, final Annotation[] annotations, final MediaType mediaType, final MultivaluedMap<String, String> httpHeaders, final InputStream entityStream) throws IOException {
        final Object result = super.readFrom(type, genericType, annotations, mediaType, httpHeaders, entityStream);

        final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        final Validator validator = factory.getValidator();
        final Set<ConstraintViolation<Object>> constraintViolations = validator.validate(result);
        if (!constraintViolations.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Invalid input: \n");
            for (final ConstraintViolation<Object> violation : constraintViolations) {
                sb.append("\tProperty '");
                sb.append(violation.getPropertyPath());
                sb.append("' ");
                sb.append(violation.getMessage());
                sb.append("\n");
            }
            throw new InputValidationException(sb.toString());
        }

        return result;
    }

}
