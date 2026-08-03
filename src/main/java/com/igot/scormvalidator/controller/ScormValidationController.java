package com.igot.scormvalidator.controller;

import com.igot.scormvalidator.service.ScormValidationService;
import com.igot.scormvalidator.util.ApiResponse;
import com.igot.scormvalidator.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/scorm/v1")
@RequiredArgsConstructor
public class ScormValidationController {

    private final ScormValidationService scormValidationService;

    @SuppressWarnings("unchecked")
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse> validateScormContent(
            @RequestBody Map<String, Object> requestBody,
            @RequestHeader(name = Constants.X_AUTH_TOKEN, required = true) String userAuthToken) {
        Map<String, Object> request = (Map<String, Object>) requestBody.getOrDefault(Constants.REQUEST, Map.of());
        ApiResponse response = scormValidationService.initiateValidation(request, userAuthToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/status")
    public ResponseEntity<ApiResponse> getValidationStatus(
            @RequestBody Map<String, Object> request,
            @RequestHeader(name = Constants.X_AUTH_TOKEN, required = true) String userAuthToken) {
        ApiResponse response = scormValidationService.getValidationStatus(request, userAuthToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
