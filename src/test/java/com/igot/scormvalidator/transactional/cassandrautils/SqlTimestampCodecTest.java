package com.igot.scormvalidator.transactional.cassandrautils;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SqlTimestampCodecTest {

    private final SqlTimestampCodec codec = new SqlTimestampCodec();

    @Test
    void innerToOuterConvertsInstantToEquivalentTimestamp() {
        Instant instant = Instant.parse("2026-07-30T09:20:30.441Z");

        Timestamp timestamp = codec.innerToOuter(instant);

        assertEquals(Timestamp.from(instant), timestamp);
    }

    @Test
    void innerToOuterReturnsNullForNullInput() {
        assertNull(codec.innerToOuter(null));
    }

    @Test
    void outerToInnerConvertsTimestampToEquivalentInstant() {
        Timestamp timestamp = new Timestamp(1753867230441L);

        Instant instant = codec.outerToInner(timestamp);

        assertEquals(timestamp.toInstant(), instant);
    }

    @Test
    void outerToInnerReturnsNullForNullInput() {
        assertNull(codec.outerToInner(null));
    }

    @Test
    void roundTripPreservesInstant() {
        Instant original = Instant.parse("2026-07-30T09:20:30.441Z");

        Instant roundTripped = codec.outerToInner(codec.innerToOuter(original));

        assertEquals(original, roundTripped);
    }
}
