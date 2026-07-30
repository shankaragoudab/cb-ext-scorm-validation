package com.igot.scormvalidator.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.igot.scormvalidator.util.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CassandraUtil {

    private static final CassandraPropertyReader propertiesCache = CassandraPropertyReader.getInstance();

    private CassandraUtil() {
    }

    /**
     * Builds "INSERT INTO ks.table(col1,col2) VALUES (?,?);" from a map's key set,
     * bound later against the same map's value iteration order.
     */
    public static String getPreparedStatement(String keyspaceName, String tableName, Map<String, Object> map) {
        StringBuilder query = new StringBuilder();
        query.append(Constants.INSERT_INTO).append(keyspaceName).append(Constants.DOT_SEPARATOR).append(tableName).append(Constants.OPEN_BRACE);
        Set<String> keySet = map.keySet();
        query.append(String.join(Constants.COMMA, keySet)).append(Constants.VALUES_WITH_BRACE);
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < keySet.size(); i++) {
            placeholders.append(Constants.QUE_MARK);
            if (i != keySet.size() - 1) {
                placeholders.append(Constants.COMMA);
            }
        }
        query.append(placeholders).append(Constants.CLOSING_BRACE);
        return query.toString();
    }

    /**
     * Builds "UPDATE ks.table SET col1=?,col2=? WHERE key1=?;" from the update-attributes and
     * key-column key sets, bound later against those same maps' value iteration order (attributes first, then keys).
     */
    public static String getUpdateStatement(String keyspaceName, String tableName, Map<String, Object> updateAttributes, Map<String, Object> keyMap) {
        StringBuilder query = new StringBuilder();
        query.append("UPDATE ").append(keyspaceName).append(Constants.DOT_SEPARATOR).append(tableName).append(" SET ");
        query.append(String.join(Constants.COMMA, updateAttributes.keySet().stream().map(k -> k + " = ?").toArray(String[]::new)));
        query.append(" WHERE ");
        query.append(String.join(" AND ", keyMap.keySet().stream().map(k -> k + " = ?").toArray(String[]::new)));
        query.append(Constants.SEMICOLON);
        return query.toString();
    }

    public static List<Map<String, Object>> createResponse(ResultSet results) {
        List<Map<String, Object>> responseList = new ArrayList<>();
        Map<String, String> columnsMapping = fetchColumnsMapping(results);
        for (Row row : results) {
            Map<String, Object> rowMap = new HashMap<>();
            columnsMapping.forEach((key, value) -> rowMap.put(key, row.getObject(value)));
            responseList.add(rowMap);
        }
        return responseList;
    }

    public static Map<String, String> fetchColumnsMapping(ResultSet results) {
        Map<String, String> columnsMapping = new HashMap<>();
        results.getColumnDefinitions().forEach(column -> {
            String property = propertiesCache.readProperty(column.getName().asInternal()).trim();
            columnsMapping.put(property, column.getName().asInternal());
        });
        return columnsMapping;
    }
}
