package com.igot.scormvalidator.transactional.cassandrautils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CassandraPropertyReaderTest {

    @Test
    void getInstanceReturnsSameSingletonEachTime() {
        CassandraPropertyReader first = CassandraPropertyReader.getInstance();
        CassandraPropertyReader second = CassandraPropertyReader.getInstance();

        assertSame(first, second, "getInstance() should always return the same singleton");
    }

    @Test
    void readPropertyTranslatesKnownColumnName() {
        String property = CassandraPropertyReader.getInstance().readProperty("resourceid");

        assertEquals("resourceId", property);
    }

    @Test
    void readPropertyFallsBackToKeyItselfWhenUnmapped() {
        String property = CassandraPropertyReader.getInstance().readProperty("nosuchcolumn");

        assertEquals("nosuchcolumn", property);
    }
}
