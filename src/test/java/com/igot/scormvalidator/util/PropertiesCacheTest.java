package com.igot.scormvalidator.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PropertiesCacheTest {

    @Test
    void getInstanceReturnsSameSingletonEachTime() {
        PropertiesCache first = PropertiesCache.getInstance();
        PropertiesCache second = PropertiesCache.getInstance();

        assertSame(first, second, "getInstance() should always return the same singleton");
    }

    @Test
    void getPropertyReadsFromClasspathPropertiesFile() {
        String value = PropertiesCache.getInstance().getProperty(Constants.CASSANDRA_CONFIG_HOST);

        assertEquals("localhost", value);
    }

    @Test
    void getPropertyFallsBackToKeyItselfWhenMissing() {
        String value = PropertiesCache.getInstance().getProperty("no.such.property.exists");

        assertEquals("no.such.property.exists", value);
    }

    @Test
    void readPropertyReturnsNullWhenMissing() {
        String value = PropertiesCache.getInstance().readProperty("no.such.property.exists");

        assertNull(value);
    }

    @Test
    void readPropertyReadsFromClasspathPropertiesFile() {
        String value = PropertiesCache.getInstance().readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL);

        assertEquals("ONE", value);
    }
}
