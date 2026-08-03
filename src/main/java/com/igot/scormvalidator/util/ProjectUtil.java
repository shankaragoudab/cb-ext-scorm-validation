package com.igot.scormvalidator.util;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public class ProjectUtil {

    private ProjectUtil() {
    }

    public static ApiResponse createDefaultResponse(String api) {
        ApiResponse response = new ApiResponse();
        response.setId(api);
        response.setVer(Constants.API_VERSION_1);
        response.setResponseCode(HttpStatus.OK);
        response.setTs(LocalDateTime.now().toString());
        response.put(Constants.STATUS, Constants.SUCCESS);
        return response;
    }

    public static void errorResponse(ApiResponse response, String errorMessage, HttpStatus httpStatus) {
        response.setResponseCode(httpStatus);
        response.put(Constants.ERROR_MESSAGE, errorMessage);
        response.put(Constants.STATUS, Constants.FAILED);
    }
}
