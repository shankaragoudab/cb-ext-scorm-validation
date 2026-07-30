package com.igot.scormvalidator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/**
 * Lazily-loaded singleton over classpath application.properties, with
 * environment-variable overrides. Used by static-init auth code
 * ({@link com.igot.scormvalidator.authentication.util.AccessTokenValidator})
 * that runs before Spring's Environment is available.
 */
public class PropertiesCache {

    private static final Logger logger = LoggerFactory.getLogger(PropertiesCache.class.getName());
    private static PropertiesCache instance;
    private final Properties properties = new Properties();

    private static final String[] FILE_NAMES = {
            "cassandra.config.properties",
            "application.properties"
    };

    private PropertiesCache() {
        for (String fileName : FILE_NAMES) {
            try (InputStream stream = PropertiesCache.class.getClassLoader().getResourceAsStream(fileName)) {
                if (stream != null) {
                    properties.load(stream);
                }
            } catch (Exception e) {
                logger.error("PropertiesCache: exception while loading {}", fileName, e);
            }
        }
    }

    public static synchronized PropertiesCache getInstance() {
        if (instance == null) {
            instance = new PropertiesCache();
        }
        return instance;
    }

    public String getProperty(String key) {
        String envValue = System.getenv(key);
        if (envValue != null) {
            return envValue;
        }
        return properties.getProperty(key, key);
    }

    public String readProperty(String key) {
        String envValue = System.getenv(key);
        if (envValue != null) {
            return envValue;
        }
        return properties.getProperty(key);
    }
}
