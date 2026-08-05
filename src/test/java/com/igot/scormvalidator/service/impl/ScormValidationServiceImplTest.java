package com.igot.scormvalidator.service.impl;

import com.igot.scormvalidator.content.ContentInfoService;
import com.igot.scormvalidator.producer.Producer;
import com.igot.scormvalidator.util.ApiResponse;
import com.igot.scormvalidator.util.Constants;
import com.igot.scormvalidator.util.ScormValidatorServerProperties;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScormValidationServiceImplTest {

    private static final String USER_AUTH_TOKEN = "some-token";
    private static final String USER_ID = "user-1";
    private static final String CONTENT_ID = "do_parent_123";
    private static final String RESOURCE_ID = "do_123";
    private static final String SUPPORTED_MIME_TYPE = "application/vnd.ekstep.html-archive";

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private Producer kafkaProducer;

    @Mock
    private ScormValidatorServerProperties serverProperties;

    @Mock
    private ContentInfoService contentInfoService;

    @InjectMocks
    private ScormValidationServiceImpl service;

    private void mockValidUser() {
        lenient().when(accessTokenValidator.fetchUserIdFromAccessToken(USER_AUTH_TOKEN)).thenReturn(USER_ID);
        lenient().when(serverProperties.getSupportedMimeTypes()).thenReturn(List.of(SUPPORTED_MIME_TYPE));
    }

    private Map<String, Object> requestWithIds(String contentId, String resourceId) {
        Map<String, Object> request = new HashMap<>();
        if (contentId != null) {
            request.put(Constants.CONTENT_ID, contentId);
        }
        if (resourceId != null) {
            request.put(Constants.RESOURCE_ID, resourceId);
        }
        return request;
    }

    private Map<String, Object> parentContentWithLeafNodes(String... leafNodes) {
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.LEAF_NODES, new ArrayList<>(List.of(leafNodes)));
        return content;
    }

    private Map<String, Object> resourceContentWithArtifact(String artifactUrl) {
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.MIME_TYPE, SUPPORTED_MIME_TYPE);
        content.put(Constants.ARTIFACT_URL, artifactUrl);
        return content;
    }

    @Test
    void initiateValidationReturnsBadRequestWhenRequestBodyEmpty() {
        ApiResponse response = service.initiateValidation(Collections.emptyMap(), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.MISSING_REQUEST_BODY, (String) response.get(Constants.ERROR_MESSAGE));
        verify(accessTokenValidator, never()).fetchUserIdFromAccessToken(anyString());
    }

    @Test
    void initiateValidationReturnsUnauthorizedWhenTokenInvalid() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(USER_AUTH_TOKEN)).thenReturn(null);

        ApiResponse response = service.initiateValidation(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenContentIdMissing() {
        mockValidUser();

        ApiResponse response = service.initiateValidation(requestWithIds(null, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.MISSING_CONTENT_ID, (String) response.get(Constants.ERROR_MESSAGE));
        verify(contentInfoService, never()).readContent(anyString());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenResourceIdMissing() {
        mockValidUser();

        ApiResponse response = service.initiateValidation(requestWithIds(CONTENT_ID, null), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.MISSING_RESOURCE_ID, (String) response.get(Constants.ERROR_MESSAGE));
        verify(contentInfoService, never()).readContent(anyString());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenContentIdNotFound() {
        mockValidUser();
        when(contentInfoService.readContent(CONTENT_ID)).thenReturn(Collections.emptyMap());

        ApiResponse response = service.initiateValidation(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(contentInfoService, never()).readContent(RESOURCE_ID);
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenResourceIdNotInLeafNodesList() {
        mockValidUser();
        when(contentInfoService.readContent(CONTENT_ID))
                .thenReturn(parentContentWithLeafNodes("do_other_1", "do_other_2"));

        ApiResponse response = service.initiateValidation(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(((String) response.get(Constants.ERROR_MESSAGE)).contains(Constants.RESOURCE_NOT_PART_OF_CONTENT));
        verify(contentInfoService, never()).readContent(RESOURCE_ID);
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenContentHasNoLeafNodesField() {
        mockValidUser();
        Map<String, Object> parentContent = new HashMap<>();
        parentContent.put("someOtherField", "value");
        when(contentInfoService.readContent(CONTENT_ID)).thenReturn(parentContent);

        ApiResponse response = service.initiateValidation(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(((String) response.get(Constants.ERROR_MESSAGE)).contains(Constants.RESOURCE_NOT_PART_OF_CONTENT));
        verify(contentInfoService, never()).readContent(RESOURCE_ID);
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenResourceContentNotFound() {
        mockValidUser();
        when(contentInfoService.readContent(CONTENT_ID)).thenReturn(parentContentWithLeafNodes(RESOURCE_ID));
        when(contentInfoService.readContent(RESOURCE_ID)).thenReturn(Collections.emptyMap());

        ApiResponse response = service.initiateValidation(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenMimeTypeUnsupported() {
        mockValidUser();
        when(contentInfoService.readContent(CONTENT_ID)).thenReturn(parentContentWithLeafNodes(RESOURCE_ID));
        Map<String, Object> resourceContent = new HashMap<>();
        resourceContent.put(Constants.MIME_TYPE, "application/pdf");
        resourceContent.put(Constants.ARTIFACT_URL, "https://example.com/file.pdf");
        when(contentInfoService.readContent(RESOURCE_ID)).thenReturn(resourceContent);

        ApiResponse response = service.initiateValidation(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(((String) response.get(Constants.ERROR_MESSAGE)).contains(Constants.UNSUPPORTED_MIME_TYPE));
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenArtifactUrlMissing() {
        mockValidUser();
        when(contentInfoService.readContent(CONTENT_ID)).thenReturn(parentContentWithLeafNodes(RESOURCE_ID));
        Map<String, Object> resourceContent = new HashMap<>();
        resourceContent.put(Constants.MIME_TYPE, SUPPORTED_MIME_TYPE);
        when(contentInfoService.readContent(RESOURCE_ID)).thenReturn(resourceContent);

        ApiResponse response = service.initiateValidation(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void initiateValidationPersistsRecordAndPublishesEventOnSuccess() {
        mockValidUser();
        when(contentInfoService.readContent(CONTENT_ID)).thenReturn(parentContentWithLeafNodes(RESOURCE_ID));
        when(contentInfoService.readContent(RESOURCE_ID))
                .thenReturn(resourceContentWithArtifact("https://example.com/path/course.zip?token=abc"));
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(successResponse());
        when(serverProperties.getScormValidationRequestedTopic()).thenReturn("scorm.validation.requested");

        ApiResponse response = service.initiateValidation(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.ACCEPTED, response.getResponseCode());
        assertEquals(CONTENT_ID, response.get(Constants.CONTENT_ID));
        assertEquals(RESOURCE_ID, response.get(Constants.RESOURCE_ID));
        assertEquals(Constants.STATUS_STARTED, response.get(Constants.STATUS));
        assertNotNull(response.get(Constants.VALIDATION_ID));

        ArgumentCaptor<Map<String, Object>> recordCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation).insertRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_SCORM_VALIDATION_STATUS), recordCaptor.capture());
        Map<String, Object> persisted = recordCaptor.getValue();
        assertEquals("course.zip", persisted.get(Constants.FILE_NAME));
        assertEquals(CONTENT_ID, persisted.get(Constants.CONTENT_ID));
        assertEquals(RESOURCE_ID, persisted.get(Constants.RESOURCE_ID));
        assertEquals(USER_ID, persisted.get(Constants.REQUESTED_BY));
        assertEquals(false, persisted.get(Constants.IS_ENHANCED));
        assertEquals(0, persisted.get(Constants.RETRY_COUNT));

        ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaProducer).pushWithKey(eq("scorm.validation.requested"), eventCaptor.capture(), eq(RESOURCE_ID));
        Map<String, Object> event = eventCaptor.getValue();
        assertEquals(Constants.EVENT_TYPE_SCORM_VALIDATION_REQUESTED, event.get(Constants.EVENT_TYPE));
        assertEquals(Constants.RESOURCE_TYPE_COURSE, event.get(Constants.RESOURCE_TYPE));
        assertNotNull(event.get(Constants.TRACE_ID));
        assertEquals(CONTENT_ID, event.get(Constants.CONTENT_ID));
        assertEquals(RESOURCE_ID, event.get(Constants.RESOURCE_ID));
        assertNotNull(event.get(Constants.VALIDATION_ID));
        // The event only carries identifiers — the consumer re-reads everything else (artifactUrl,
        // fileName, status, retry count, etc.) straight from Cassandra, so none of that should be
        // duplicated onto the Kafka message.
        assertEquals(6, event.size(),
                "event should only contain contentId, resourceId, validationId, eventType, resourceType and traceId");
        assertFalse(event.containsKey(Constants.ARTIFACT_URL));
        assertFalse(event.containsKey(Constants.FILE_NAME));
    }

    @Test
    void initiateValidationExtractsFileNameWithoutQueryString() {
        mockValidUser();
        when(contentInfoService.readContent(CONTENT_ID)).thenReturn(parentContentWithLeafNodes(RESOURCE_ID));
        when(contentInfoService.readContent(RESOURCE_ID))
                .thenReturn(resourceContentWithArtifact("https://example.com/path/to/artifact.zip"));
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(successResponse());
        when(serverProperties.getScormValidationRequestedTopic()).thenReturn("scorm.validation.requested");

        service.initiateValidation(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        ArgumentCaptor<Map<String, Object>> recordCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation).insertRecord(anyString(), anyString(), recordCaptor.capture());
        assertEquals("artifact.zip", recordCaptor.getValue().get(Constants.FILE_NAME));
    }

    @Test
    void initiateValidationReturnsInternalServerErrorWhenInsertFails() {
        mockValidUser();
        when(contentInfoService.readContent(CONTENT_ID)).thenReturn(parentContentWithLeafNodes(RESOURCE_ID));
        when(contentInfoService.readContent(RESOURCE_ID))
                .thenReturn(resourceContentWithArtifact("https://example.com/course.zip"));
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(failureResponse());

        ApiResponse response = service.initiateValidation(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        verify(kafkaProducer, never()).pushWithKey(anyString(), any(), anyString());
    }

    @Test
    void getValidationStatusReturnsUnauthorizedWhenTokenInvalid() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(USER_AUTH_TOKEN)).thenReturn(null);

        ApiResponse response = service.getValidationStatus(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        verify(cassandraOperation, never()).getRecordsByProperties(anyString(), anyString(), anyMap(), any(), anyInt());
    }

    @Test
    void getValidationStatusReturnsBadRequestWhenContentIdMissing() {
        mockValidUser();

        ApiResponse response = service.getValidationStatus(requestWithIds(null, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.MISSING_CONTENT_ID, (String) response.get(Constants.ERROR_MESSAGE));
        verify(cassandraOperation, never()).getRecordsByProperties(anyString(), anyString(), anyMap(), any(), anyInt());
    }

    @Test
    void getValidationStatusSingleLookupReturnsNotFoundWhenNoRecords() {
        mockValidUser();
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = service.getValidationStatus(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void getValidationStatusSingleLookupReturnsResourcesArrayWithOneRecord() {
        mockValidUser();
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.CONTENT_ID, CONTENT_ID);
        record.put(Constants.RESOURCE_ID, RESOURCE_ID);
        record.put(Constants.STATUS, Constants.STATUS_STARTED);
        List<Map<String, Object>> records = List.of(record);

        ArgumentCaptor<Map<String, Object>> keyCaptor = ArgumentCaptor.forClass(Map.class);
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_SCORM_VALIDATION_STATUS), keyCaptor.capture(), any(), eq(1)))
                .thenReturn(records);

        ApiResponse response = service.getValidationStatus(requestWithIds(CONTENT_ID, RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(CONTENT_ID, response.get(Constants.CONTENT_ID));
        assertEquals(records, response.get(Constants.RESOURCES));
        assertEquals(CONTENT_ID, keyCaptor.getValue().get(Constants.CONTENT_ID));
        assertEquals(RESOURCE_ID, keyCaptor.getValue().get(Constants.RESOURCE_ID));
    }

    @Test
    void getValidationStatusListLookupReturnsAllRowsForContentId() {
        mockValidUser();
        Map<String, Object> record1 = new HashMap<>();
        record1.put(Constants.CONTENT_ID, CONTENT_ID);
        record1.put(Constants.RESOURCE_ID, "do_123");
        record1.put(Constants.STATUS, Constants.STATUS_STARTED);
        Map<String, Object> record2 = new HashMap<>();
        record2.put(Constants.CONTENT_ID, CONTENT_ID);
        record2.put(Constants.RESOURCE_ID, "do_124");
        record2.put(Constants.STATUS, Constants.STATUS_COMPLETED);
        List<Map<String, Object>> records = List.of(record1, record2);

        ArgumentCaptor<Map<String, Object>> keyCaptor = ArgumentCaptor.forClass(Map.class);
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_SCORM_VALIDATION_STATUS), keyCaptor.capture(), any(), any()))
                .thenReturn(records);

        ApiResponse response = service.getValidationStatus(requestWithIds(CONTENT_ID, null), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(CONTENT_ID, response.get(Constants.CONTENT_ID));
        assertEquals(records, response.get(Constants.RESOURCES));
        assertEquals(CONTENT_ID, keyCaptor.getValue().get(Constants.CONTENT_ID));
        assertFalse(keyCaptor.getValue().containsKey(Constants.RESOURCE_ID));
    }

    @Test
    void getValidationStatusListLookupReturnsEmptyResourcesNotNotFound() {
        mockValidUser();
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = service.getValidationStatus(requestWithIds(CONTENT_ID, null), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(CONTENT_ID, response.get(Constants.CONTENT_ID));
        assertEquals(Collections.emptyList(), response.get(Constants.RESOURCES));
    }

    private org.igot.common.ApiResponse successResponse() {
        org.igot.common.ApiResponse response = new org.igot.common.ApiResponse();
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    private org.igot.common.ApiResponse failureResponse() {
        org.igot.common.ApiResponse response = new org.igot.common.ApiResponse();
        response.put(Constants.RESPONSE, "failed");
        return response;
    }
}
