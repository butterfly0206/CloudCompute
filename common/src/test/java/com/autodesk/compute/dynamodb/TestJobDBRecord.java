package com.autodesk.compute.dynamodb;

import com.autodesk.compute.model.dynamodb.JobDBRecord;
import com.autodesk.compute.test.categories.UnitTests;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Category(UnitTests.class)
@Slf4j
public class TestJobDBRecord {
    @Test
    public void testCreationTime() {
        final JobDBRecord record = JobDBRecord.createDefault("someid");

        final List<Long> timestamps = Arrays.asList(
                System.currentTimeMillis(),
                Instant.parse("2021-02-20T23:20:07.206Z").toEpochMilli());

        for (final long tsMilliseconds : timestamps) {
            final long tsSeconds = tsMilliseconds / 1_000;
            if (tsSeconds < 1_609_372_800) {
                // The old way, if we stored timestamp prior to Dec 31 2020 12:00 am as seconds
                // we should receive it converted to milliseconds
                record.setCreationTime(tsSeconds);
                assertEquals(tsSeconds * 1_000, record.getCreationTimeMillis());
            }
            // When stored as millisecond we should receive the exact number
            record.setCreationTime(tsMilliseconds);
            assertEquals(tsMilliseconds, record.getCreationTimeMillis());
        }
    }
}
