package com.autodesk.compute.logging;

import com.autodesk.compute.common.ComputeStringOps;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public class MDCLoader implements AutoCloseable {


    private final Map<String, String> loggingFieldsMap;

    public MDCLoader(final Map<String, String> loggingFieldsMap) {
        this.loggingFieldsMap = loggingFieldsMap != null ? new HashMap<>(loggingFieldsMap) : new HashMap<>();
        this.loggingFieldsMap.forEach((k, v) -> MDC.put(k, v));
    }

    private static MDCLoader empty() {
        return new MDCLoader(new HashMap<>());
    }

    public static MDCLoader forField(final String fieldName, final String fieldValue) {
        return empty().withField(fieldName, fieldValue);
    }

    public static MDCLoader forFieldMap(Map<String, String> fieldMap) {
        MDCLoader loader = empty();
        fieldMap.forEach((k, v) -> loader.withField(k, v));
        return loader;
    }

    public static MDCLoader forRequestId(final String requestId) {
        return MDCLoader.forField(MDCFields.REQUEST_ID, requestId);
    }

    public static MDCLoader forMoniker(final String moniker) {
        return MDCLoader.forField(MDCFields.MONIKER, moniker);
    }

    @Override
    public void close() {
        loggingFieldsMap.forEach((k, v) -> MDC.remove(k));
    }

    // Method is mutating the object on purpose; otherwise we lose the mdc context handle
    public MDCLoader withField(final String fieldName, final String fieldValue) {
        if (!ComputeStringOps.isNullOrEmpty(fieldName) && !ComputeStringOps.isNullOrEmpty(fieldValue)) {
            loggingFieldsMap.put(fieldName, fieldValue);
            MDC.put(fieldName, fieldValue);
        }
        return this;
    }

    public MDCLoader withMoniker(final String moniker) {
        return withField(MDCFields.MONIKER, moniker);
    }
    
}