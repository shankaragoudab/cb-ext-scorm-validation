package com.igot.scormvalidator.controller;

import com.igot.scormvalidator.service.ScormValidationService;
import com.igot.scormvalidator.util.ApiResponse;
import com.igot.scormvalidator.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScormValidationControllerTest {

    private static final String AUTH_TOKEN = "some-token";
    private static final String CONTENT_ID = "do_parent_123";
    private static final String RESOURCE_ID = "do_123";

    @Mock
    private ScormValidationService scormValidationService;

    @InjectMocks
    private ScormValidationController controller;

    private Map<String, Object> statusRequest(String contentId, String resourceId) {
        Map<String, Object> request = new HashMap<>();
        if (contentId != null) {
            request.put(Constants.CONTENT_ID, contentId);
        }
        if (resourceId != null) {
            request.put(Constants.RESOURCE_ID, resourceId);
        }
        return request;
    }

    /** Wraps contentId/resourceId under "request", matching the real /validate request body. */
    private Map<String, Object> validateRequestBody(String contentId, String resourceId) {
        return Map.of(Constants.REQUEST, statusRequest(contentId, resourceId));
    }

    private ApiResponse serviceResponseWithResult(HttpStatus status, String resultKey, Object resultValue) {
        ApiResponse response = new ApiResponse();
        response.setId(Constants.API_SCORM_VALIDATE);
        response.setVer(Constants.API_VERSION_1);
        response.setTs("2026-07-30T09:20:30.441Z");
        response.setResponseCode(status);
        response.put(resultKey, resultValue);
        return response;
    }

    @Test
    void validateScormContentUnwrapsRequestAndDelegatesToService() {
        Map<String, Object> body = validateRequestBody(CONTENT_ID, RESOURCE_ID);
        Map<String, Object> expectedInner = statusRequest(CONTENT_ID, RESOURCE_ID);
        ApiResponse serviceResponse = serviceResponseWithResult(HttpStatus.ACCEPTED, Constants.STATUS, Constants.STATUS_STARTED);
        when(scormValidationService.initiateValidation(expectedInner, AUTH_TOKEN)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse> result = controller.validateScormContent(body, AUTH_TOKEN);

        assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
        assertEquals(serviceResponse, result.getBody());
        verify(scormValidationService).initiateValidation(expectedInner, AUTH_TOKEN);
    }

    @Test
    void validateScormContentTreatsMissingRequestWrapperAsEmptyMap() {
        ApiResponse serviceResponse = serviceResponseWithResult(HttpStatus.BAD_REQUEST, Constants.ERROR_MESSAGE, Constants.MISSING_REQUEST_BODY);
        when(scormValidationService.initiateValidation(Map.of(), AUTH_TOKEN)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse> result = controller.validateScormContent(new HashMap<>(), AUTH_TOKEN);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        verify(scormValidationService).initiateValidation(Map.of(), AUTH_TOKEN);
    }

    @Test
    void validateScormContentPropagatesErrorResponseCodeFromService() {
        Map<String, Object> body = validateRequestBody(null, null);
        ApiResponse serviceResponse = serviceResponseWithResult(HttpStatus.UNAUTHORIZED, Constants.ERROR_MESSAGE, Constants.INVALID_AUTH_TOKEN);
        when(scormValidationService.initiateValidation(new HashMap<>(), AUTH_TOKEN)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse> result = controller.validateScormContent(body, AUTH_TOKEN);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void getValidationStatusDelegatesToServiceAndReturnsItsResponseCodeForSingleLookup() {
        Map<String, Object> request = statusRequest(CONTENT_ID, RESOURCE_ID);
        ApiResponse serviceResponse = serviceResponseWithResult(HttpStatus.OK, Constants.STATUS, Constants.STATUS_STARTED);
        when(scormValidationService.getValidationStatus(request, AUTH_TOKEN)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse> result = controller.getValidationStatus(request, AUTH_TOKEN);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(serviceResponse, result.getBody());
        verify(scormValidationService).getValidationStatus(request, AUTH_TOKEN);
    }

    @Test
    void getValidationStatusDelegatesToServiceAndReturnsItsResponseCodeForListLookup() {
        Map<String, Object> request = statusRequest(CONTENT_ID, null);
        ApiResponse serviceResponse = serviceResponseWithResult(HttpStatus.OK, Constants.CONTENT_ID, CONTENT_ID);
        when(scormValidationService.getValidationStatus(request, AUTH_TOKEN)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse> result = controller.getValidationStatus(request, AUTH_TOKEN);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(serviceResponse, result.getBody());
        verify(scormValidationService).getValidationStatus(request, AUTH_TOKEN);
    }

    @Test
    void getValidationStatusPropagatesNotFoundResponseCodeFromService() {
        Map<String, Object> request = statusRequest(CONTENT_ID, RESOURCE_ID);
        ApiResponse serviceResponse = serviceResponseWithResult(HttpStatus.NOT_FOUND, Constants.ERROR_MESSAGE, Constants.VALIDATION_NOT_FOUND);
        when(scormValidationService.getValidationStatus(request, AUTH_TOKEN)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse> result = controller.getValidationStatus(request, AUTH_TOKEN);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }
}
