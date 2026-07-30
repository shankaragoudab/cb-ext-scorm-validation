package com.igot.scormvalidator.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.igot.scormvalidator.util.ApiResponse;
import com.igot.scormvalidator.util.Constants;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CassandraOperationImpl implements CassandraOperation {

    private static final Logger logger = LoggerFactory.getLogger(CassandraOperationImpl.class);

    private final CassandraConnectionManager connectionManager;

    Select processQuery(String keyspaceName, String tableName, Map<String, Object> propertyMap, List<String> fields) {
        Select select = CollectionUtils.isNotEmpty(fields)
                ? QueryBuilder.selectFrom(keyspaceName, tableName).columns(fields)
                : QueryBuilder.selectFrom(keyspaceName, tableName).all();
        if (MapUtils.isEmpty(propertyMap)) {
            return select;
        }
        for (Map.Entry<String, Object> entry : propertyMap.entrySet()) {
            select = select.whereColumn(entry.getKey()).isEqualTo(QueryBuilder.literal(entry.getValue()));
        }
        return select;
    }

    @Override
    public ApiResponse insertRecord(String keyspaceName, String tableName, Map<String, Object> request) {
        ApiResponse response = new ApiResponse();
        try {
            String query = CassandraUtil.getPreparedStatement(keyspaceName, tableName, request);
            CqlSession session = connectionManager.getSession(keyspaceName);
            PreparedStatement statement = session.prepare(query);
            BoundStatement boundStatement = statement.bind(request.values().toArray());
            session.execute(boundStatement);
            response.put(Constants.RESPONSE, Constants.SUCCESS);
        } catch (Exception e) {
            String errMsg = String.format("Exception occurred while inserting record to %s : %s", tableName, e.getMessage());
            logger.error(errMsg, e);
            response.put(Constants.RESPONSE, Constants.FAILED);
            response.put(Constants.ERROR_MESSAGE, errMsg);
        }
        return response;
    }

    @Override
    public List<Map<String, Object>> getRecordsByPropertiesWithoutFiltering(String keyspaceName, String tableName,
                                                                             Map<String, Object> propertyMap, List<String> fields, Integer limit) {
        List<Map<String, Object>> response = new ArrayList<>();
        try {
            Select selectQuery = processQuery(keyspaceName, tableName, propertyMap, fields);
            if (limit != null) {
                selectQuery = selectQuery.limit(limit);
            }
            SimpleStatement statement = SimpleStatement.newInstance(selectQuery.toString());
            ResultSet results = connectionManager.getSession(keyspaceName).execute(statement);
            response = CassandraUtil.createResponse(results);
        } catch (Exception e) {
            logger.error("Error fetching records from {}: {}", tableName, e.getMessage(), e);
        }
        return response;
    }

    @Override
    public ApiResponse updateRecord(String keyspaceName, String tableName, Map<String, Object> updateAttributes, Map<String, Object> keyMap) {
        ApiResponse response = new ApiResponse();
        try {
            String query = CassandraUtil.getUpdateStatement(keyspaceName, tableName, updateAttributes, keyMap);
            CqlSession session = connectionManager.getSession(keyspaceName);
            PreparedStatement statement = session.prepare(query);
            Object[] values = new Object[updateAttributes.size() + keyMap.size()];
            int i = 0;
            for (Object value : updateAttributes.values()) {
                values[i++] = value;
            }
            for (Object value : keyMap.values()) {
                values[i++] = value;
            }
            BoundStatement boundStatement = statement.bind(values);
            session.execute(boundStatement);
            response.put(Constants.RESPONSE, Constants.SUCCESS);
        } catch (Exception e) {
            String errMsg = String.format("Exception occurred while updating record in %s : %s", tableName, e.getMessage());
            logger.error(errMsg, e);
            response.put(Constants.RESPONSE, Constants.FAILED);
            response.put(Constants.ERROR_MESSAGE, errMsg);
        }
        return response;
    }
}
