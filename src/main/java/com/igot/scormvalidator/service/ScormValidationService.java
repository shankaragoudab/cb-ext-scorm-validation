package com.igot.scormvalidator.service;

import com.igot.scormvalidator.util.ApiResponse;

import java.util.Map;

public interface ScormValidationService {

    ApiResponse initiateValidation(Map<String, Object> request, String userAuthToken);

    ApiResponse getValidationStatus(String resourceId, String userAuthToken);
}
