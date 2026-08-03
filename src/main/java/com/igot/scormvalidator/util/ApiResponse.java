package com.igot.scormvalidator.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API response payload returned to clients: id/ver/ts identify the API call, responseCode is the
 * HTTP status (serialized as its name), and result carries the actual data.
 */
public class ApiResponse {

    private String id;
    private String ver;
    private String ts;

    @JsonIgnore
    private HttpStatus responseCode;

    private final Map<String, Object> result = new LinkedHashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVer() {
        return ver;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    @JsonIgnore
    public HttpStatus getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(HttpStatus responseCode) {
        this.responseCode = responseCode;
    }

    @JsonProperty("responseCode")
    public String getResponseCodeName() {
        return responseCode != null ? responseCode.name() : null;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void put(String key, Object value) {
        result.put(key, value);
    }

    public Object get(String key) {
        return result.get(key);
    }
}
