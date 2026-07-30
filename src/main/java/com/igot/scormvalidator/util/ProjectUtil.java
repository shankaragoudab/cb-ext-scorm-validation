package com.igot.scormvalidator.util;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class ProjectUtil {

    private ProjectUtil() {
    }

    public static ApiResponse createDefaultResponse(String api) {
        ApiResponse response = new ApiResponse();
        response.setId(api);
        response.setVer(Constants.API_VERSION_1);
        response.setParams(new ApiRespParam(UUID.randomUUID().toString()));
        response.getParams().setStatus(Constants.SUCCESS);
        response.setResponseCode(HttpStatus.OK);
        response.setTs(LocalDateTime.now().toString());
        return response;
    }

    public static void errorResponse(ApiResponse response, String errorMessage, HttpStatus httpStatus) {
        response.setResponseCode(httpStatus);
        response.getParams().setErrMsg(errorMessage);
        response.getParams().setStatus(Constants.FAILED);
    }
}
