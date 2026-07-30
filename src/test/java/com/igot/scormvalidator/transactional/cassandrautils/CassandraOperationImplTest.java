package com.igot.scormvalidator.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.igot.scormvalidator.util.ApiResponse;
import com.igot.scormvalidator.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CassandraOperationImplTest {

    @Mock
    private CassandraConnectionManager connectionManager;

    @Mock
    private CqlSession session;

    @InjectMocks
    private CassandraOperationImpl operation;

    @Test
    void processQueryBuildsSelectAllWhenNoFieldsRequested() {
        Map<String, Object> propertyMap = new LinkedHashMap<>();
        propertyMap.put(Constants.RESOURCE_ID, "resource-1");

        Select select = operation.processQuery(Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, propertyMap, null);

        String query = select.toString();
        assertTrue(query.contains("SELECT * FROM " + Constants.KEYSPACE_SUNBIRD + "." + Constants.TABLE_SCORM_VALIDATION_STATUS));
        assertTrue(query.contains("resourceid"));
    }

    @Test
    void processQueryBuildsSelectWithRequestedColumnsAndNoWhereClauseWhenPropertyMapEmpty() {
        Select select = operation.processQuery(Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, Map.of(), List.of("status", "fileName"));

        String query = select.toString();
        assertTrue(query.contains("SELECT status,filename FROM " + Constants.KEYSPACE_SUNBIRD + "." + Constants.TABLE_SCORM_VALIDATION_STATUS));
        assertTrue(!query.toUpperCase().contains("WHERE"));
    }

    @Test
    void insertRecordReturnsSuccessWhenSessionExecutesCleanly() {
        lenient().when(connectionManager.getSession(anyString())).thenReturn(session);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        BoundStatement boundStatement = mock(BoundStatement.class);
        when(session.prepare(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.bind(any(Object[].class))).thenReturn(boundStatement);
        when(session.execute(any(Statement.class))).thenReturn(mock(ResultSet.class));

        Map<String, Object> record = new LinkedHashMap<>();
        record.put(Constants.RESOURCE_ID, "resource-1");

        ApiResponse response = operation.insertRecord(Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, record);

        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    }

    @Test
    void insertRecordReturnsFailedWhenSessionThrows() {
        lenient().when(connectionManager.getSession(anyString())).thenReturn(session);
        when(session.prepare(anyString())).thenThrow(new RuntimeException("boom"));

        Map<String, Object> record = new LinkedHashMap<>();
        record.put(Constants.RESOURCE_ID, "resource-1");

        ApiResponse response = operation.insertRecord(Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, record);

        assertEquals(Constants.FAILED, response.get(Constants.RESPONSE));
        assertTrue(((String) response.get(Constants.ERROR_MESSAGE)).contains("boom"));
    }

    @Test
    void updateRecordReturnsSuccessWhenSessionExecutesCleanly() {
        lenient().when(connectionManager.getSession(anyString())).thenReturn(session);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        BoundStatement boundStatement = mock(BoundStatement.class);
        when(session.prepare(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.bind(any(Object[].class))).thenReturn(boundStatement);
        when(session.execute(any(Statement.class))).thenReturn(mock(ResultSet.class));

        Map<String, Object> updateAttributes = new LinkedHashMap<>();
        updateAttributes.put(Constants.STATUS, Constants.STATUS_COMPLETED);
        Map<String, Object> keyMap = new LinkedHashMap<>();
        keyMap.put(Constants.RESOURCE_ID, "resource-1");

        ApiResponse response = operation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, updateAttributes, keyMap);

        assertEquals(Constants.SUCCESS, response.get(Constants.RESPONSE));
    }

    @Test
    void updateRecordReturnsFailedWhenSessionThrows() {
        lenient().when(connectionManager.getSession(anyString())).thenReturn(session);
        when(session.prepare(anyString())).thenThrow(new RuntimeException("update failed"));

        Map<String, Object> updateAttributes = new LinkedHashMap<>();
        updateAttributes.put(Constants.STATUS, Constants.STATUS_FAILED);
        Map<String, Object> keyMap = new LinkedHashMap<>();
        keyMap.put(Constants.RESOURCE_ID, "resource-1");

        ApiResponse response = operation.updateRecord(Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, updateAttributes, keyMap);

        assertEquals(Constants.FAILED, response.get(Constants.RESPONSE));
    }

    @Test
    void getRecordsByPropertiesWithoutFilteringReturnsMappedRowsOnSuccess() {
        when(connectionManager.getSession(anyString())).thenReturn(session);

        ColumnDefinition resourceIdColumn = mock(ColumnDefinition.class);
        when(resourceIdColumn.getName()).thenReturn(CqlIdentifier.fromInternal(Constants.RESOURCE_ID.toLowerCase()));
        ColumnDefinitions columnDefinitions = mock(ColumnDefinitions.class, CALLS_REAL_METHODS);
        when(columnDefinitions.iterator()).thenReturn(List.of(resourceIdColumn).iterator());

        Row row = mock(Row.class);
        when(row.getObject(Constants.RESOURCE_ID.toLowerCase())).thenReturn("resource-1");

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getColumnDefinitions()).thenReturn(columnDefinitions);
        when(resultSet.iterator()).thenReturn(List.of(row).iterator());
        when(session.execute(any(Statement.class))).thenReturn(resultSet);

        Map<String, Object> propertyMap = new LinkedHashMap<>();
        propertyMap.put(Constants.RESOURCE_ID, "resource-1");

        List<Map<String, Object>> records = operation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, propertyMap, null, 1);

        assertEquals(1, records.size());
        assertEquals("resource-1", records.get(0).get(Constants.RESOURCE_ID));
    }

    @Test
    void getRecordsByPropertiesWithoutFilteringReturnsEmptyListWhenSessionThrows() {
        when(connectionManager.getSession(anyString())).thenReturn(session);
        when(session.execute(any(Statement.class))).thenThrow(new RuntimeException("query failed"));

        Map<String, Object> propertyMap = new LinkedHashMap<>();
        propertyMap.put(Constants.RESOURCE_ID, "resource-1");

        List<Map<String, Object>> records = operation.getRecordsByPropertiesWithoutFiltering(
                Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, propertyMap, null, 1);

        assertTrue(records.isEmpty());
    }
}
