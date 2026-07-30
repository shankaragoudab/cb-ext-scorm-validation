package com.igot.scormvalidator.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProjectUtilTest {

    @Test
    void createDefaultResponseBuildsSuccessfulOkResponse() {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.scorm.validate");

        assertEquals("api.scorm.validate", response.getId());
        assertEquals(Constants.API_VERSION_1, response.getVer());
        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(Constants.SUCCESS, response.getParams().getStatus());
        assertNotNull(response.getParams().getResMsgId());
        assertNotNull(response.getTs());
    }

    @Test
    void errorResponseSetsFailureStatusAndMessage() {
        ApiResponse response = ProjectUtil.createDefaultResponse("api.test");

        ProjectUtil.errorResponse(response, "boom", HttpStatus.BAD_REQUEST);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.FAILED, response.getParams().getStatus());
        assertEquals("boom", response.getParams().getErrMsg());
    }
}
