package com.igot.scormvalidator.controller;

import com.igot.scormvalidator.service.ScormValidationService;
import com.igot.scormvalidator.util.ApiResponse;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScormValidationControllerTest {

    private static final String AUTH_TOKEN = "some-token";
    private static final String RESOURCE_ID = "do_123";

    @Mock
    private ScormValidationService scormValidationService;

    @InjectMocks
    private ScormValidationController controller;

    @Test
    void validateScormContentDelegatesToServiceAndReturnsItsResponseCode() {
        Map<String, Object> request = new HashMap<>();
        request.put("resourceId", RESOURCE_ID);
        ApiResponse serviceResponse = new ApiResponse();
        serviceResponse.setResponseCode(HttpStatus.ACCEPTED);
        when(scormValidationService.initiateValidation(request, AUTH_TOKEN)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse> result = controller.validateScormContent(request, AUTH_TOKEN);

        assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
        assertSame(serviceResponse, result.getBody());
        verify(scormValidationService).initiateValidation(request, AUTH_TOKEN);
    }

    @Test
    void validateScormContentPropagatesErrorResponseCodeFromService() {
        Map<String, Object> request = new HashMap<>();
        ApiResponse serviceResponse = new ApiResponse();
        serviceResponse.setResponseCode(HttpStatus.UNAUTHORIZED);
        when(scormValidationService.initiateValidation(request, AUTH_TOKEN)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse> result = controller.validateScormContent(request, AUTH_TOKEN);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void getValidationStatusDelegatesToServiceAndReturnsItsResponseCode() {
        ApiResponse serviceResponse = new ApiResponse();
        serviceResponse.setResponseCode(HttpStatus.OK);
        when(scormValidationService.getValidationStatus(RESOURCE_ID, AUTH_TOKEN)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse> result = controller.getValidationStatus(RESOURCE_ID, AUTH_TOKEN);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(serviceResponse, result.getBody());
        verify(scormValidationService).getValidationStatus(eq(RESOURCE_ID), eq(AUTH_TOKEN));
    }

    @Test
    void getValidationStatusPropagatesNotFoundResponseCodeFromService() {
        ApiResponse serviceResponse = new ApiResponse();
        serviceResponse.setResponseCode(HttpStatus.NOT_FOUND);
        when(scormValidationService.getValidationStatus(RESOURCE_ID, AUTH_TOKEN)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse> result = controller.getValidationStatus(RESOURCE_ID, AUTH_TOKEN);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }
}
