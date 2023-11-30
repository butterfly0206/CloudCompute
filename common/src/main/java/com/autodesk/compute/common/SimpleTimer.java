package com.autodesk.compute.common;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class SimpleTimer implements AutoCloseable {
    private final String section;
    private final Instant start;
    public SimpleTimer(String section) {
        this.section = section;
        this.start = Instant.now();
    }

    @Override
    public void close() {
        Instant end = Instant.now();
        log.info("{} took {} msec", section, Duration.between(start, end).toMillis());
    }
}
