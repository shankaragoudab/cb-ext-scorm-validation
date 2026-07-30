package com.igot.scormvalidator.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * The OSS driver's default TIMESTAMP codec only accepts java.time.Instant;
 * this wraps it so java.sql.Timestamp values bind directly to CQL timestamp columns.
 */
public class SqlTimestampCodec extends MappingCodec<Instant, Timestamp> {

    public SqlTimestampCodec() {
        super(TypeCodecs.TIMESTAMP, GenericType.of(Timestamp.class));
    }

    @Override
    protected Timestamp innerToOuter(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    @Override
    protected Instant outerToInner(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
