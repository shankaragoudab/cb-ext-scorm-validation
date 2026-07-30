package com.igot.scormvalidator.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;

public interface CassandraConnectionManager {
    CqlSession getSession(String keyspace);
}
