package com.igot.scormvalidator.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.igot.scormvalidator.util.Constants;
import com.igot.scormvalidator.util.PropertiesCache;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The class is final and its constructor eagerly opens a real Cassandra connection, so it can't be
 * subclassed/spied to bypass networking the way the sibling services do. Only the network-free
 * helpers (getConsistencyLevel, and createCassandraConnectionWithKeySpaces' host validation, which
 * runs before any socket is opened) are exercised here, using MockedStatic to control PropertiesCache
 * without touching the real classpath application.properties.
 */
class CassandraConnectionManagerImplTest {

    @Test
    void getConsistencyLevelReadsConfiguredValueFromProperties() {
        ConsistencyLevel level = CassandraConnectionManagerImpl.getConsistencyLevel();

        assertEquals(DefaultConsistencyLevel.ONE, level);
    }

    @Test
    void getConsistencyLevelHonorsMockedConfiguredLevel() {
        PropertiesCache mockCache = mock(PropertiesCache.class);
        when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");

        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(mockCache);

            ConsistencyLevel level = CassandraConnectionManagerImpl.getConsistencyLevel();

            assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, level);
        }
    }

    @Test
    void getConsistencyLevelFallsBackToDefaultWhenPropertyBlank() {
        PropertiesCache mockCache = mock(PropertiesCache.class);
        when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("");

        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(mockCache);

            ConsistencyLevel level = CassandraConnectionManagerImpl.getConsistencyLevel();

            assertEquals(DefaultConsistencyLevel.valueOf(Constants.DEFAULT_SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL), level);
        }
    }

    @Test
    void createCassandraConnectionWithKeySpacesThrowsWhenHostNotConfigured() {
        PropertiesCache mockCache = mock(PropertiesCache.class);
        when(mockCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");

        try (MockedStatic<PropertiesCache> propertiesCacheMock = mockStatic(PropertiesCache.class)) {
            propertiesCacheMock.when(PropertiesCache::getInstance).thenReturn(mockCache);

            // The constructor eagerly calls createCassandraConnectionWithKeySpaces(null), so the
            // missing-host validation fires before any socket is opened, without needing a live broker.
            IllegalStateException exception = assertThrows(IllegalStateException.class, CassandraConnectionManagerImpl::new);
            assertEquals("Cassandra host is not configured", exception.getMessage());
        }
    }
}
