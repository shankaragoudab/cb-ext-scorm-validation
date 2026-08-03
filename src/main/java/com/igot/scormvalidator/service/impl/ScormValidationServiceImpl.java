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

        if (MapUtils.isEmpty(request)) {
            ProjectUtil.errorResponse(response, Constants.MISSING_REQUEST_BODY, HttpStatus.BAD_REQUEST);
            return response;
        }

        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userAuthToken);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED);
            return response;
        }

        String contentId = (String) request.get(Constants.CONTENT_ID);
        if (StringUtils.isBlank(contentId)) {
            ProjectUtil.errorResponse(response, Constants.MISSING_CONTENT_ID, HttpStatus.BAD_REQUEST);
            return response;
        }

        String resourceId = (String) request.get(Constants.RESOURCE_ID);
        if (StringUtils.isBlank(resourceId)) {
            ProjectUtil.errorResponse(response, Constants.MISSING_RESOURCE_ID, HttpStatus.BAD_REQUEST);
            return response;
        }

        Map<String, Object> parentContent = contentInfoService.readContent(contentId);
        if (MapUtils.isEmpty(parentContent)) {
            ProjectUtil.errorResponse(response, Constants.CONTENT_NOT_FOUND + contentId, HttpStatus.BAD_REQUEST);
            return response;
        }

        if (!isResourcePartOfContent(parentContent, resourceId)) {
            ProjectUtil.errorResponse(response, Constants.RESOURCE_NOT_PART_OF_CONTENT + resourceId, HttpStatus.BAD_REQUEST);
            return response;
        }

        Map<String, Object> resourceContent = contentInfoService.readContent(resourceId);
        if (MapUtils.isEmpty(resourceContent)) {
            ProjectUtil.errorResponse(response, Constants.CONTENT_NOT_FOUND + resourceId, HttpStatus.BAD_REQUEST);
            return response;
        }

        String mimeType = (String) resourceContent.get(Constants.MIME_TYPE);
        if (!serverProperties.getSupportedMimeTypes().contains(mimeType)) {
            ProjectUtil.errorResponse(response, Constants.UNSUPPORTED_MIME_TYPE + resourceId, HttpStatus.BAD_REQUEST);
            return response;
        }

        String artifactUrl = (String) resourceContent.get(Constants.ARTIFACT_URL);
        if (StringUtils.isBlank(artifactUrl)) {
            ProjectUtil.errorResponse(response, Constants.ARTIFACT_URL_NOT_FOUND + resourceId, HttpStatus.BAD_REQUEST);
            return response;
        }
        String fileName = extractFileName(artifactUrl);

        String validationId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        Map<String, Object> trackingRecord = new HashMap<>();
        trackingRecord.put(Constants.VALIDATION_ID, validationId);
        trackingRecord.put(Constants.CONTENT_ID, contentId);
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
        response.put(Constants.CONTENT_ID, contentId);
        response.put(Constants.RESOURCE_ID, resourceId);
        response.put(Constants.CREATED_AT, now);
        return response;
    }

    /**
     * A content that isn't a collection may have no {@code leafNodes} field at all (or a
     * non-list/empty value) — in that case there is nothing to match {@code resourceId} against,
     * so membership fails just as if the list existed but didn't contain it.
     */
    @SuppressWarnings("unchecked")
    private boolean isResourcePartOfContent(Map<String, Object> parentContent, String resourceId) {
        Object leafNodesObj = parentContent.get(Constants.LEAF_NODES);
        if (!(leafNodesObj instanceof List) || ((List<Object>) leafNodesObj).isEmpty()) {
            return false;
        }
        List<Object> leafNodes = (List<Object>) leafNodesObj;
        for (Object leafNode : leafNodes) {
            if (String.valueOf(leafNode).equals(resourceId)) {
                return true;
            }
        }
        return false;
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

    /**
     * Publishes only the identifiers the consumer needs to look the record back up in Cassandra
     * (contentId/resourceId/validationId) plus trace/event metadata — not the full tracking
     * record. The consumer re-reads the current row (artifactUrl, retry count, etc.) from
     * Cassandra when it picks the event up, so there's no need to duplicate that data onto the
     * Kafka message.
     */
    private void publishValidationRequestedEvent(Map<String, Object> trackingRecord) {
        Map<String, Object> event = new HashMap<>();
        event.put(Constants.CONTENT_ID, trackingRecord.get(Constants.CONTENT_ID));
        event.put(Constants.RESOURCE_ID, trackingRecord.get(Constants.RESOURCE_ID));
        event.put(Constants.VALIDATION_ID, trackingRecord.get(Constants.VALIDATION_ID));
        event.put(Constants.EVENT_TYPE, Constants.EVENT_TYPE_SCORM_VALIDATION_REQUESTED);
        event.put(Constants.RESOURCE_TYPE, Constants.RESOURCE_TYPE_COURSE);
        event.put(Constants.TRACE_ID, UUID.randomUUID().toString());
        kafkaProducer.pushWithKey(serverProperties.getScormValidationRequestedTopic(), event, (String) trackingRecord.get(Constants.RESOURCE_ID));
    }

    @Override
    public ApiResponse getValidationStatus(Map<String, Object> request, String userAuthToken) {
        ApiResponse response = ProjectUtil.createDefaultResponse(Constants.API_SCORM_VALIDATE_STATUS);

        String userId = accessTokenValidator.fetchUserIdFromAccessToken(userAuthToken);
        if (StringUtils.isBlank(userId)) {
            ProjectUtil.errorResponse(response, Constants.INVALID_AUTH_TOKEN, HttpStatus.UNAUTHORIZED);
            return response;
        }

        String contentId = (String) request.get(Constants.CONTENT_ID);
        if (StringUtils.isBlank(contentId)) {
            ProjectUtil.errorResponse(response, Constants.MISSING_CONTENT_ID, HttpStatus.BAD_REQUEST);
            return response;
        }

        String resourceId = (String) request.get(Constants.RESOURCE_ID);
        Map<String, Object> keyMap = new HashMap<>();
        keyMap.put(Constants.CONTENT_ID, contentId);
        Integer limit = null;
        if (StringUtils.isNotBlank(resourceId)) {
            keyMap.put(Constants.RESOURCE_ID, resourceId);
            limit = 1;
        }

        List<Map<String, Object>> records = cassandraOperation.getRecordsByProperties(
                Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, keyMap, null, limit);

        if (StringUtils.isNotBlank(resourceId) && records.isEmpty()) {
            ProjectUtil.errorResponse(response, Constants.VALIDATION_NOT_FOUND, HttpStatus.NOT_FOUND);
            return response;
        }
        response.setResponseCode(HttpStatus.OK);
        response.put(Constants.CONTENT_ID, contentId);
        response.put(Constants.RESOURCES, records);
        return response;
    }
}
