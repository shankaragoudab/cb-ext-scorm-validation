package com.igot.scormvalidator.service.impl;

import com.igot.scormvalidator.authentication.util.AccessTokenValidator;
import com.igot.scormvalidator.content.ContentInfoService;
import com.igot.scormvalidator.producer.Producer;
import com.igot.scormvalidator.transactional.cassandrautils.CassandraOperation;
import com.igot.scormvalidator.util.ApiResponse;
import com.igot.scormvalidator.util.Constants;
import com.igot.scormvalidator.util.ScormValidatorServerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private static final String RESOURCE_ID = "do_123";

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
    }

    private Map<String, Object> requestWithResourceId(String resourceId) {
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.RESOURCE_ID, resourceId);
        return request;
    }

    @Test
    void initiateValidationReturnsUnauthorizedWhenTokenInvalid() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(USER_AUTH_TOKEN)).thenReturn(null);

        ApiResponse response = service.initiateValidation(requestWithResourceId(RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenResourceIdMissing() {
        mockValidUser();

        ApiResponse response = service.initiateValidation(new HashMap<>(), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(contentInfoService, never()).readContent(anyString());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenContentNotFound() {
        mockValidUser();
        when(contentInfoService.readContent(RESOURCE_ID)).thenReturn(Collections.emptyMap());

        ApiResponse response = service.initiateValidation(requestWithResourceId(RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void initiateValidationReturnsBadRequestWhenArtifactUrlMissing() {
        mockValidUser();
        Map<String, Object> content = new HashMap<>();
        content.put("someOtherField", "value");
        when(contentInfoService.readContent(RESOURCE_ID)).thenReturn(content);

        ApiResponse response = service.initiateValidation(requestWithResourceId(RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        verify(cassandraOperation, never()).insertRecord(anyString(), anyString(), anyMap());
    }

    @Test
    void initiateValidationPersistsRecordAndPublishesEventOnSuccess() {
        mockValidUser();
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.ARTIFACT_URL, "https://example.com/path/course.zip?token=abc");
        when(contentInfoService.readContent(RESOURCE_ID)).thenReturn(content);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(successResponse());
        when(serverProperties.getScormValidationRequestedTopic()).thenReturn("scorm.validation.requested");

        ApiResponse response = service.initiateValidation(requestWithResourceId(RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.ACCEPTED, response.getResponseCode());
        assertEquals(RESOURCE_ID, response.get(Constants.RESOURCE_ID));
        assertEquals(Constants.STATUS_STARTED, response.get(Constants.STATUS));
        assertNotNull(response.get(Constants.VALIDATION_ID));

        ArgumentCaptor<Map<String, Object>> recordCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation).insertRecord(eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_SCORM_VALIDATION_STATUS), recordCaptor.capture());
        Map<String, Object> persisted = recordCaptor.getValue();
        assertEquals("course.zip", persisted.get(Constants.FILE_NAME));
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
    }

    @Test
    void initiateValidationExtractsFileNameWithoutQueryString() {
        mockValidUser();
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.ARTIFACT_URL, "https://example.com/path/to/artifact.zip");
        when(contentInfoService.readContent(RESOURCE_ID)).thenReturn(content);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(successResponse());
        when(serverProperties.getScormValidationRequestedTopic()).thenReturn("scorm.validation.requested");

        service.initiateValidation(requestWithResourceId(RESOURCE_ID), USER_AUTH_TOKEN);

        ArgumentCaptor<Map<String, Object>> recordCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation).insertRecord(anyString(), anyString(), recordCaptor.capture());
        assertEquals("artifact.zip", recordCaptor.getValue().get(Constants.FILE_NAME));
    }

    @Test
    void initiateValidationReturnsInternalServerErrorWhenInsertFails() {
        mockValidUser();
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.ARTIFACT_URL, "https://example.com/course.zip");
        when(contentInfoService.readContent(RESOURCE_ID)).thenReturn(content);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(failureResponse());

        ApiResponse response = service.initiateValidation(requestWithResourceId(RESOURCE_ID), USER_AUTH_TOKEN);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getResponseCode());
        verify(kafkaProducer, never()).pushWithKey(anyString(), any(), anyString());
    }

    @Test
    void getValidationStatusReturnsUnauthorizedWhenTokenInvalid() {
        when(accessTokenValidator.fetchUserIdFromAccessToken(USER_AUTH_TOKEN)).thenReturn(null);

        ApiResponse response = service.getValidationStatus(RESOURCE_ID, USER_AUTH_TOKEN);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getResponseCode());
        verify(cassandraOperation, never()).getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), any(), anyInt());
    }

    @Test
    void getValidationStatusReturnsNotFoundWhenNoRecords() {
        mockValidUser();
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        ApiResponse response = service.getValidationStatus(RESOURCE_ID, USER_AUTH_TOKEN);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void getValidationStatusReturnsOkWithRecordWhenFound() {
        mockValidUser();
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.RESOURCE_ID, RESOURCE_ID);
        record.put(Constants.STATUS, Constants.STATUS_STARTED);
        List<Map<String, Object>> records = List.of(record);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_SCORM_VALIDATION_STATUS), anyMap(), any(), eq(1)))
                .thenReturn(records);

        ApiResponse response = service.getValidationStatus(RESOURCE_ID, USER_AUTH_TOKEN);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(RESOURCE_ID, response.get(Constants.RESOURCE_ID));
        assertEquals(Constants.STATUS_STARTED, response.get(Constants.STATUS));
    }

    private ApiResponse successResponse() {
        ApiResponse response = new ApiResponse();
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }

    private ApiResponse failureResponse() {
        ApiResponse response = new ApiResponse();
        response.put(Constants.RESPONSE, "failed");
        return response;
    }
}
