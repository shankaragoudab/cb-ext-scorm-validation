package com.igot.scormvalidator.controller;

import com.igot.scormvalidator.service.ScormValidationService;
import com.igot.scormvalidator.util.ApiResponse;
import com.igot.scormvalidator.util.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/v1/scorm")
@RequiredArgsConstructor
public class ScormValidationController {

    private final ScormValidationService scormValidationService;

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse> validateScormContent(
            @RequestBody Map<String, Object> request,
            @RequestHeader(Constants.X_AUTH_TOKEN) String userAuthToken) {
        ApiResponse response = scormValidationService.initiateValidation(request, userAuthToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/getvalidationStatus/{resourceId}")
    public ResponseEntity<ApiResponse> getValidationStatus(
            @PathVariable String resourceId,
            @RequestHeader(Constants.X_AUTH_TOKEN) String userAuthToken) {
        ApiResponse response = scormValidationService.getValidationStatus(resourceId, userAuthToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
