package com.igot.scormvalidator.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.retry.DefaultRetryPolicy;
import com.datastax.oss.driver.internal.core.time.AtomicTimestampGenerator;
import com.igot.scormvalidator.util.Constants;
import com.igot.scormvalidator.util.PropertiesCache;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public final class CassandraConnectionManagerImpl implements CassandraConnectionManager {

    private static final Logger logger = LoggerFactory.getLogger(CassandraConnectionManagerImpl.class);
    private final Map<String, CqlSession> cassandraSessionMap = new ConcurrentHashMap<>(2);
    private CqlSession session;

    public CassandraConnectionManagerImpl() {
        registerShutdownHook();
        createCassandraConnection();
    }

    @Override
    public CqlSession getSession(String keyspaceName) {
        CqlSession currentSession = cassandraSessionMap.get(keyspaceName);
        if (currentSession != null && !currentSession.isClosed()) {
            return currentSession;
        }
        CqlSession newSession = createCassandraConnectionWithKeySpaces(keyspaceName);
        cassandraSessionMap.put(keyspaceName, newSession);
        return newSession;
    }

    CqlSession createCassandraConnectionWithKeySpaces(String keySpaceName) {
        PropertiesCache cache = PropertiesCache.getInstance();
        String cassandraHost = cache.getProperty(Constants.CASSANDRA_CONFIG_HOST);
        if (StringUtils.isBlank(cassandraHost)) {
            throw new IllegalStateException("Cassandra host is not configured");
        }
        List<String> hosts = Arrays.asList(cassandraHost.split(","));
        List<InetSocketAddress> contactPoints = hosts.stream()
                .map(host -> new InetSocketAddress(host.trim(), 9042))
                .collect(Collectors.toList());
        List<String> contactPointsString = hosts.stream()
                .map(host -> host.trim() + ":9042")
                .collect(Collectors.toList());

        DriverConfigLoader loader = DriverConfigLoader.programmaticBuilder()
                .withStringList(DefaultDriverOption.CONTACT_POINTS, contactPointsString)
                .withString(DefaultDriverOption.REQUEST_CONSISTENCY, getConsistencyLevel().name())
                .withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, Constants.LOCAL_DATACENTER)
                .withInt(DefaultDriverOption.CONNECTION_POOL_LOCAL_SIZE,
                        Integer.parseInt(cache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)))
                .withInt(DefaultDriverOption.CONNECTION_POOL_REMOTE_SIZE,
                        Integer.parseInt(cache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)))
                .withInt(DefaultDriverOption.HEARTBEAT_INTERVAL,
                        Integer.parseInt(cache.getProperty(Constants.HEARTBEAT_INTERVAL)))
                .withInt(DefaultDriverOption.CONNECTION_INIT_QUERY_TIMEOUT, 10000)
                .withInt(DefaultDriverOption.REQUEST_TIMEOUT, 10000)
                .withString(DefaultDriverOption.PROTOCOL_VERSION, ProtocolVersion.V4.toString())
                .withClass(DefaultDriverOption.RETRY_POLICY_CLASS, DefaultRetryPolicy.class)
                .withClass(DefaultDriverOption.TIMESTAMP_GENERATOR_CLASS, AtomicTimestampGenerator.class)
                .build();

        try {
            CqlSessionBuilder builder = CqlSession.builder()
                    .addContactPoints(contactPoints)
                    .withLocalDatacenter(Constants.LOCAL_DATACENTER)
                    .withConfigLoader(loader)
                    .addTypeCodecs(new SqlTimestampCodec());
            if (StringUtils.isNotBlank(keySpaceName)) {
                builder = builder.withKeyspace(keySpaceName);
            }
            CqlSession sessionWithKeyspace = builder.build();
            logger.info("Connected to the keyspace: {}", keySpaceName);
            Metadata metadata = sessionWithKeyspace.getMetadata();
            logger.info("Connected to cluster: {}", metadata.getClusterName());
            for (Node node : metadata.getNodes().values()) {
                logger.info("Datacenter: {}; Host: {}; Rack: {}", node.getDatacenter(), node.getEndPoint(), node.getRack());
            }
            return sessionWithKeyspace;
        } catch (Exception e) {
            logger.error("Error while creating Cassandra connection", e);
            throw new IllegalStateException("Error while creating Cassandra connection: " + e.getMessage(), e);
        }
    }

    void createCassandraConnection() {
        session = createCassandraConnectionWithKeySpaces(null);
    }

    static ConsistencyLevel getConsistencyLevel() {
        String consistency = PropertiesCache.getInstance().readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL);
        if (StringUtils.isBlank(consistency)) {
            consistency = Constants.DEFAULT_SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL;
        }
        return DefaultConsistencyLevel.valueOf(consistency.toUpperCase());
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Started resource cleanup for Cassandra.");
            cassandraSessionMap.values().forEach(CqlSession::close);
            if (session != null) {
                session.close();
            }
            logger.info("Completed resource cleanup for Cassandra.");
        }));
    }
}
