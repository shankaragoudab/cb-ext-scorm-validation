package com.igot.scormvalidator.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CassandraUtilTest {

    @Test
    void getPreparedStatementBuildsInsertWithPlaceholdersInMapOrder() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("validationid", "v-1");
        record.put("resourceid", "r-1");
        record.put("status", "STARTED");

        String query = CassandraUtil.getPreparedStatement("sunbird", "scorm_validation_status", record);

        assertEquals("INSERT INTO sunbird.scorm_validation_status(validationid,resourceid,status) VALUES (?,?,?);", query);
    }

    @Test
    void getPreparedStatementHandlesSingleColumn() {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("resourceid", "r-1");

        String query = CassandraUtil.getPreparedStatement("sunbird", "scorm_validation_status", record);

        assertEquals("INSERT INTO sunbird.scorm_validation_status(resourceid) VALUES (?);", query);
    }

    @Test
    void getUpdateStatementBuildsUpdateWithSetAndWhereClauses() {
        Map<String, Object> updateAttributes = new LinkedHashMap<>();
        updateAttributes.put("status", "COMPLETED");
        updateAttributes.put("updatedat", "2026-07-30");
        Map<String, Object> keyMap = new LinkedHashMap<>();
        keyMap.put("resourceid", "r-1");

        String query = CassandraUtil.getUpdateStatement("sunbird", "scorm_validation_status", updateAttributes, keyMap);

        assertEquals("UPDATE sunbird.scorm_validation_status SET status = ?,updatedat = ? WHERE resourceid = ?;", query);
    }

    @Test
    void getUpdateStatementSupportsCompositeKey() {
        Map<String, Object> updateAttributes = new LinkedHashMap<>();
        updateAttributes.put("status", "FAILED");
        Map<String, Object> keyMap = new LinkedHashMap<>();
        keyMap.put("rootorgid", "org-1");
        keyMap.put("identifier", "id-1");

        String query = CassandraUtil.getUpdateStatement("sunbird", "user_bulk_upload", updateAttributes, keyMap);

        assertEquals("UPDATE sunbird.user_bulk_upload SET status = ? WHERE rootorgid = ? AND identifier = ?;", query);
    }

    @Test
    void fetchColumnsMappingTranslatesInternalColumnNamesToCamelCaseKeys() {
        ResultSet resultSet = resultSetWithColumns("resourceid", "filename");

        Map<String, String> mapping = CassandraUtil.fetchColumnsMapping(resultSet);

        assertEquals("resourceid", mapping.get("resourceId"));
        assertEquals("filename", mapping.get("fileName"));
    }

    @Test
    void createResponseMapsEachRowUsingColumnMapping() {
        ColumnDefinition resourceIdColumn = columnDefinition("resourceid");
        ColumnDefinitions columnDefinitions = mock(ColumnDefinitions.class, CALLS_REAL_METHODS);
        when(columnDefinitions.iterator()).thenReturn(List.of(resourceIdColumn).iterator());

        Row row = mock(Row.class);
        when(row.getObject("resourceid")).thenReturn("do_12345");

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getColumnDefinitions()).thenReturn(columnDefinitions);
        when(resultSet.iterator()).thenReturn(List.of(row).iterator());

        List<Map<String, Object>> rows = CassandraUtil.createResponse(resultSet);

        assertEquals(1, rows.size());
        assertEquals("do_12345", rows.get(0).get("resourceId"));
    }

    @Test
    void createResponseReturnsEmptyListWhenNoRows() {
        ColumnDefinitions columnDefinitions = mock(ColumnDefinitions.class, CALLS_REAL_METHODS);
        when(columnDefinitions.iterator()).thenReturn(List.<ColumnDefinition>of().iterator());

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getColumnDefinitions()).thenReturn(columnDefinitions);
        when(resultSet.iterator()).thenReturn(List.<Row>of().iterator());

        List<Map<String, Object>> rows = CassandraUtil.createResponse(resultSet);

        assertTrue(rows.isEmpty());
    }

    private ResultSet resultSetWithColumns(String... internalNames) {
        ColumnDefinitions columnDefinitions = mock(ColumnDefinitions.class, CALLS_REAL_METHODS);
        List<ColumnDefinition> defs = List.of(internalNames).stream().map(this::columnDefinition).toList();
        when(columnDefinitions.iterator()).thenReturn(defs.iterator());

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getColumnDefinitions()).thenReturn(columnDefinitions);
        return resultSet;
    }

    private ColumnDefinition columnDefinition(String internalName) {
        ColumnDefinition columnDefinition = mock(ColumnDefinition.class);
        when(columnDefinition.getName()).thenReturn(CqlIdentifier.fromInternal(internalName));
        return columnDefinition;
    }
}
