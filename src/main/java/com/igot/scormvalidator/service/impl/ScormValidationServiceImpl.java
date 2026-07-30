package com.igot.scormvalidator.service.impl;

import com.igot.scormvalidator.content.ContentInfoService;
import com.igot.scormvalidator.producer.Producer;
import com.igot.scormvalidator.service.ScormValidationService;
import com.igot.scormvalidator.util.ApiResponse;
import com.igot.scormvalidator.util.Constants;
import com.igot.scormvalidator.util.ProjectUtil;
import com.igot.scormvalidator.util.ScormValidatorServerProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScormValidationServiceImpl implements ScormValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ScormValidationServiceImpl.class);

    private final AccessTokenValidator accessTokenValidator;
    private final CassandraOperation cassandraOperation;
    private final Producer kafkaProducer;
    private final ScormValidatorServerProperties serverProperties;
    private final ContentInfoService contentInfoService;

    @Override
    public ApiResponse initiateValidation(Map<String, Object> request, String userAuthToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_SCORM_VALIDATE);

        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userAuthToken);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED);
            return response;
        }

        String resourceId = (String) request.get(Constants.RESOURCE_ID);
        if (StringUtils.isBlank(resourceId)) {
            ProjectUtil.errorResponse(response, Constants.MISSING_REQUIRED_FIELDS, HttpStatus.BAD_REQUEST);
            return response;
        }

        Map<String, Object> content = contentInfoService.readContent(resourceId);
        if (MapUtils.isEmpty(content)) {
            ProjectUtil.errorResponse(response, Constants.CONTENT_NOT_FOUND + resourceId, HttpStatus.BAD_REQUEST);
            return response;
        }

        String artifactUrl = (String) content.get(Constants.ARTIFACT_URL);
        if (StringUtils.isBlank(artifactUrl)) {
            ProjectUtil.errorResponse(response, Constants.ARTIFACT_URL_NOT_FOUND + resourceId, HttpStatus.BAD_REQUEST);
            return response;
        }
        String fileName = extractFileName(artifactUrl);

        String validationId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, Object> trackingRecord = new HashMap<>();
        trackingRecord.put(Constants.VALIDATION_ID, validationId);
        trackingRecord.put(Constants.RESOURCE_ID, resourceId);
        trackingRecord.put(Constants.FILE_NAME, fileName);
        trackingRecord.put(Constants.ARTIFACT_URL, artifactUrl);
        trackingRecord.put(Constants.STATUS, Constants.STATUS_STARTED);
        trackingRecord.put(Constants.IS_ENHANCED, false);
        trackingRecord.put(Constants.REQUESTED_BY, userId);
        trackingRecord.put(Constants.CREATED_AT, now);
        trackingRecord.put(Constants.UPDATED_AT, now);
        trackingRecord.put(Constants.RETRY_COUNT, 0);

        org.igot.common.ApiResponse insertResponse = (org.igot.common.ApiResponse) cassandraOperation.insertRecord(
                Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, trackingRecord);
        if (!Constants.SUCCESS.equalsIgnoreCase((String) insertResponse.get(Constants.RESPONSE))) {
            ProjectUtil.errorResponse(response, "Failed to persist SCORM validation record", HttpStatus.INTERNAL_SERVER_ERROR);
            return response;
        }

        publishValidationRequestedEvent(trackingRecord);

        response.setResponseCode(HttpStatus.ACCEPTED);
        response.put(Constants.VALIDATION_ID, validationId);
        response.put(Constants.STATUS, Constants.STATUS_STARTED);
        response.put(Constants.RESOURCE_ID, resourceId);
        response.put(Constants.CREATED_AT, now);
        return response;
    }

    private String extractFileName(String artifactUrl) {
        String path = artifactUrl;
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }
        int slashIdx = path.lastIndexOf('/');
        return slashIdx >= 0 ? path.substring(slashIdx + 1) : path;
    }

    private void publishValidationRequestedEvent(Map<String, Object> trackingRecord) {
        Map<String, Object> event = new HashMap<>(trackingRecord);
        event.put(Constants.EVENT_TYPE, Constants.EVENT_TYPE_SCORM_VALIDATION_REQUESTED);
        event.put(Constants.RESOURCE_TYPE, Constants.RESOURCE_TYPE_COURSE);
        event.put(Constants.TRACE_ID, UUID.randomUUID().toString());
        kafkaProducer.pushWithKey(serverProperties.getScormValidationRequestedTopic(), event, (String) trackingRecord.get(Constants.RESOURCE_ID));
    }

    @Override
    public ApiResponse getValidationStatus(String resourceId, String userAuthToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_SCORM_VALIDATE_STATUS);

        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userAuthToken);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED);
            return response;
        }

        Map<String, Object> keyMap = new HashMap<>();
        keyMap.put(Constants.RESOURCE_ID, resourceId);
        List<Map<String, Object>> records = cassandraOperation.getRecordsByProperties(
                Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, keyMap, null, 1);

        if (records.isEmpty()) {
            ProjectUtil.errorResponse(response, Constants.VALIDATION_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }

        response.setResponseCode(HttpStatus.OK);
        response.getResult().putAll(records.get(0));
        return response;
    }
}
