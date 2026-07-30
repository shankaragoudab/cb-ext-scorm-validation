package com.igot.scormvalidator.content;

import com.igot.scormvalidator.util.ScormValidatorServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentInfoServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ScormValidatorServerProperties serverProperties;

    @InjectMocks
    private ContentInfoService contentInfoService;

    @BeforeEach
    void setUp() {
        lenient().when(serverProperties.getContentServiceHost()).thenReturn("http://content-service:9000/");
        lenient().when(serverProperties.getContentReadEndpoint()).thenReturn("content/v4/admin/read");
    }

    @Test
    void readContentReturnsEmptyMapForBlankContentId() {
        assertTrue(contentInfoService.readContent(" ").isEmpty());
        assertTrue(contentInfoService.readContent(null).isEmpty());
    }

    @Test
    void readContentReturnsContentMapWhenResponseCodeIsOk() {
        Map<String, Object> content = new HashMap<>();
        content.put("artifactUrl", "https://example.com/file.zip");

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);

        Map<String, Object> response = new HashMap<>();
        response.put("responseCode", "OK");
        response.put("result", result);

        when(restTemplate.getForObject("http://content-service:9000/content/v4/admin/read/do_123", Map.class)).thenReturn(response);

        Map<String, Object> actual = contentInfoService.readContent("do_123");

        assertTrue(actual.containsKey("artifactUrl"));
        assertEquals("https://example.com/file.zip", actual.get("artifactUrl"));
    }

    @Test
    void readContentReturnsEmptyMapWhenResponseCodeIsNotOk() {
        Map<String, Object> response = new HashMap<>();
        response.put("responseCode", "CLIENT_ERROR");

        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);

        assertTrue(contentInfoService.readContent("do_123").isEmpty());
    }

    @Test
    void readContentReturnsEmptyMapWhenResultMissing() {
        Map<String, Object> response = new HashMap<>();
        response.put("responseCode", "OK");

        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);

        assertTrue(contentInfoService.readContent("do_123").isEmpty());
    }

    @Test
    void readContentReturnsEmptyMapWhenRestCallThrows() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertTrue(contentInfoService.readContent("do_123").isEmpty());
    }

    @Test
    void readContentReturnsEmptyMapWhenResponseIsNull() {
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);

        assertTrue(contentInfoService.readContent("do_123").isEmpty());
    }
}
