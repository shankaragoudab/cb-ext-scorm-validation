package com.igot.scormvalidator.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiRespParamTest {

    @Test
    void testDefaultConstructor() {
        ApiRespParam params = new ApiRespParam();
        assertNull(params.getResMsgId(), "ResMsgId should be null for default constructor");
        assertNull(params.getMsgId(), "MsgId should be null for default constructor");
        assertNull(params.getErr(), "Err should be null for default constructor");
        assertNull(params.getStatus(), "Status should be null for default constructor");
        assertNull(params.getErrMsg(), "ErrMsg should be null for default constructor");
    }

    @Test
    void testParameterizedConstructor() {
        String id = UUID.randomUUID().toString();
        ApiRespParam params = new ApiRespParam(id);
        assertEquals(id, params.getResMsgId(), "ResMsgId should match the provided ID");
        assertEquals(id, params.getMsgId(), "MsgId should match the provided ID");
        assertNull(params.getErr(), "Err should be null initially");
        assertNull(params.getStatus(), "Status should be null initially");
        assertNull(params.getErrMsg(), "ErrMsg should be null initially");
    }

    @Test
    void testSettersAndGetters() {
        ApiRespParam params = new ApiRespParam();
        String resMsgId = "res-123";
        String msgId = "msg-456";
        String err = "ERR_001";
        String status = "FAILED";
        String errMsg = "An error occurred";
        params.setResMsgId(resMsgId);
        params.setMsgId(msgId);
        params.setErr(err);
        params.setStatus(status);
        params.setErrMsg(errMsg);
        assertEquals(resMsgId, params.getResMsgId(), "ResMsgId should be updated");
        assertEquals(msgId, params.getMsgId(), "MsgId should be updated");
        assertEquals(err, params.getErr(), "Err should be updated");
        assertEquals(status, params.getStatus(), "Status should be updated");
        assertEquals(errMsg, params.getErrMsg(), "ErrMsg should be updated");
    }

    @Test
    void testWithNullValues() {
        ApiRespParam params = new ApiRespParam("test-id");
        params.setResMsgId(null);
        params.setMsgId(null);
        params.setErr(null);
        params.setStatus(null);
        params.setErrMsg(null);
        assertNull(params.getResMsgId(), "ResMsgId should be set to null");
        assertNull(params.getMsgId(), "MsgId should be set to null");
        assertNull(params.getErr(), "Err should be set to null");
        assertNull(params.getStatus(), "Status should be set to null");
        assertNull(params.getErrMsg(), "ErrMsg should be set to null");
    }

    @Test
    void testEmptyStringValues() {
        ApiRespParam params = new ApiRespParam();
        String emptyString = "";
        params.setResMsgId(emptyString);
        params.setMsgId(emptyString);
        params.setErr(emptyString);
        params.setStatus(emptyString);
        params.setErrMsg(emptyString);
        assertEquals(emptyString, params.getResMsgId(), "ResMsgId should be set to empty string");
        assertEquals(emptyString, params.getMsgId(), "MsgId should be set to empty string");
        assertEquals(emptyString, params.getErr(), "Err should be set to empty string");
        assertEquals(emptyString, params.getStatus(), "Status should be set to empty string");
        assertEquals(emptyString, params.getErrMsg(), "ErrMsg should be set to empty string");
    }
}
