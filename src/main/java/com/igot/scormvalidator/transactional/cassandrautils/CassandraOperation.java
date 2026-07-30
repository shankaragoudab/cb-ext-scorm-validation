package com.igot.scormvalidator.transactional.cassandrautils;

import com.igot.scormvalidator.util.ApiResponse;

import java.util.List;
import java.util.Map;

public interface CassandraOperation {

    ApiResponse insertRecord(String keyspaceName, String tableName, Map<String, Object> request);

    List<Map<String, Object>> getRecordsByPropertiesWithoutFiltering(String keyspaceName, String tableName,
                                                                      Map<String, Object> propertyMap, List<String> fields, Integer limit);

    ApiResponse updateRecord(String keyspaceName, String tableName, Map<String, Object> updateAttributes, Map<String, Object> keyMap);
}
