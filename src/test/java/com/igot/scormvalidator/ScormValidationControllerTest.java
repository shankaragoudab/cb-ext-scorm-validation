package com.igot.scormvalidator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.igot.scormvalidator.content.ContentInfoService;
import com.igot.scormvalidator.util.Constants;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.igot.common.ApiResponse;
import org.igot.common.auth.AccessTokenValidator;
import org.igot.common.cassandra.CassandraConnectionManager;
import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Full Spring-context / MockMvc-level test hitting the real {@code @RequestMapping} paths (base
 * mapping is {@code /scorm/v1}, see {@code ScormValidationController}) — complements the
 * mock-based unit test in {@code com.igot.scormvalidator.controller.ScormValidationControllerTest}
 * which calls the controller's Java methods directly and doesn't exercise routing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScormValidationControllerTest {

    private static final String CONTENT_ID = "do_parent_123";
    private static final String RESOURCE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String SUPPORTED_MIME_TYPE = "application/vnd.ekstep.html-archive";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccessTokenValidator accessTokenValidator;

    @MockBean
    private CassandraOperation cassandraOperation;

    @MockBean
    private CassandraConnectionManager cassandraConnectionManager;

    @MockBean
    private ContentInfoService contentInfoService;

    private Map<String, Object> parentContentWithChildNode(String resourceId) {
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.PRIMARY_CATEGORY, "Course");
        content.put(Constants.CHILD_NODES, List.of(resourceId));
        return content;
    }

    private Map<String, Object> resourceContent(String artifactUrl) {
        Map<String, Object> content = new HashMap<>();
        content.put(Constants.MIME_TYPE, SUPPORTED_MIME_TYPE);
        content.put(Constants.ARTIFACT_URL, artifactUrl);
        return content;
    }

    @Test
    void shouldCreateValidationRequestAndReturnAccepted() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user-123");
        ApiResponse insertResponse = new ApiResponse();
        insertResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(insertResponse);

        when(contentInfoService.readContent(CONTENT_ID)).thenReturn(parentContentWithChildNode(RESOURCE_ID));
        when(contentInfoService.readContent(RESOURCE_ID))
                .thenReturn(resourceContent("https://example.com/files/course.zip"));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.CONTENT_ID, CONTENT_ID);
        request.put(Constants.RESOURCE_ID, RESOURCE_ID);

        mockMvc.perform(post("/scorm/v1/validate")
                        .header(Constants.X_AUTH_TOKEN, "dummy-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(Constants.REQUEST, request))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result.status").value("STARTED"))
                .andExpect(jsonPath("$.result.contentId").value(CONTENT_ID))
                .andExpect(jsonPath("$.result.resourceId").value(RESOURCE_ID));
    }

    @Test
    void shouldRejectRequestWithoutValidToken() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(null);

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.CONTENT_ID, CONTENT_ID);
        request.put(Constants.RESOURCE_ID, RESOURCE_ID);

        mockMvc.perform(post("/scorm/v1/validate")
                        .header(Constants.X_AUTH_TOKEN, "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(Constants.REQUEST, request))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRequestWhenContentNotFound() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user-123");
        when(contentInfoService.readContent(anyString())).thenReturn(new HashMap<>());

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.CONTENT_ID, CONTENT_ID);
        request.put(Constants.RESOURCE_ID, RESOURCE_ID);

        mockMvc.perform(post("/scorm/v1/validate")
                        .header(Constants.X_AUTH_TOKEN, "dummy-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(Constants.REQUEST, request))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnValidationStatusContainingInstantTimestampsForSingleLookup() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user-123");

        Map<String, Object> record = new HashMap<>();
        record.put(Constants.CONTENT_ID, CONTENT_ID);
        record.put(Constants.RESOURCE_ID, RESOURCE_ID);
        record.put(Constants.STATUS, Constants.STATUS_STARTED);
        record.put(Constants.CREATED_AT, Instant.parse("2026-07-30T09:20:30.441Z"));
        record.put(Constants.UPDATED_AT, Instant.parse("2026-07-30T09:20:30.441Z"));
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(record));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.CONTENT_ID, CONTENT_ID);
        request.put(Constants.RESOURCE_ID, RESOURCE_ID);

        mockMvc.perform(post("/scorm/v1/status")
                        .header(Constants.X_AUTH_TOKEN, "dummy-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value(Constants.SUCCESS))
                .andExpect(jsonPath("$.result.contentId").value(CONTENT_ID))
                .andExpect(jsonPath("$.result.resources[0].status").value("STARTED"))
                .andExpect(jsonPath("$.result.resources[0].createdAt").value("2026-07-30T09:20:30.441Z"));
    }

    @Test
    void shouldReturnValidationStatusListForContentIdOnlyLookup() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user-123");

        Map<String, Object> record = new HashMap<>();
        record.put(Constants.CONTENT_ID, CONTENT_ID);
        record.put(Constants.RESOURCE_ID, RESOURCE_ID);
        record.put(Constants.STATUS, Constants.STATUS_STARTED);
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), any()))
                .thenReturn(List.of(record));

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.CONTENT_ID, CONTENT_ID);

        mockMvc.perform(post("/scorm/v1/status")
                        .header(Constants.X_AUTH_TOKEN, "dummy-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.contentId").value(CONTENT_ID))
                .andExpect(jsonPath("$.result.resources[0].resourceId").value(RESOURCE_ID));
    }
}
