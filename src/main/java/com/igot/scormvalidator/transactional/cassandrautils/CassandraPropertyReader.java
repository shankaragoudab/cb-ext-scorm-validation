package com.igot.scormvalidator.transactional.cassandrautils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reverse-mapping of Cassandra's lowercase-folded column names to the
 * camelCase map keys used everywhere else (see cassandratablecolumn.properties).
 */
public class CassandraPropertyReader {

    private static final String FILE_NAME = "cassandratablecolumn.properties";
    private static final Logger logger = LoggerFactory.getLogger(CassandraPropertyReader.class);

    private final Properties properties = new Properties();

    private CassandraPropertyReader() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(FILE_NAME)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            logger.error("Error loading properties from file '{}'", FILE_NAME, e);
        }
    }

    public static CassandraPropertyReader getInstance() {
        return Holder.INSTANCE;
    }

    public String readProperty(String key) {
        return properties.getProperty(key, key);
    }

    private static class Holder {
        private static final CassandraPropertyReader INSTANCE = new CassandraPropertyReader();
    }
}
