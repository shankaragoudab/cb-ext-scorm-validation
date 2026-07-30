package com.igot.scormvalidator;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.igot.scormvalidator.authentication.util.AccessTokenValidator;
import com.igot.scormvalidator.content.ContentInfoService;
import com.igot.scormvalidator.transactional.cassandrautils.CassandraConnectionManager;
import com.igot.scormvalidator.transactional.cassandrautils.CassandraOperation;
import com.igot.scormvalidator.util.ApiResponse;
import com.igot.scormvalidator.util.Constants;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ScormValidationControllerTest {

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

    @Test
    void shouldCreateValidationRequestAndReturnAccepted() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user-123");
        ApiResponse insertResponse = new ApiResponse();
        insertResponse.put(Constants.RESPONSE, Constants.SUCCESS);
        when(cassandraOperation.insertRecord(anyString(), anyString(), anyMap())).thenReturn(insertResponse);

        Map<String, Object> content = new HashMap<>();
        content.put(Constants.ARTIFACT_URL, "https://example.com/files/course.zip");
        when(contentInfoService.readContent(anyString())).thenReturn(content);

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.RESOURCE_ID, "550e8400-e29b-41d4-a716-446655440000");

        mockMvc.perform(post("/v1/scorm/validate")
                        .header(Constants.X_AUTH_TOKEN, "dummy-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result.status").value("STARTED"))
                .andExpect(jsonPath("$.result.resourceId").value("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void shouldRejectRequestWithoutValidToken() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn(null);

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.RESOURCE_ID, "550e8400-e29b-41d4-a716-446655440000");

        mockMvc.perform(post("/v1/scorm/validate")
                        .header(Constants.X_AUTH_TOKEN, "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRequestWhenContentNotFound() throws Exception {
        when(accessTokenValidator.fetchUserIdFromAccessToken(anyString())).thenReturn("user-123");
        when(contentInfoService.readContent(anyString())).thenReturn(new HashMap<>());

        Map<String, Object> request = new HashMap<>();
        request.put(Constants.RESOURCE_ID, "550e8400-e29b-41d4-a716-446655440000");

        mockMvc.perform(post("/v1/scorm/validate")
                        .header(Constants.X_AUTH_TOKEN, "dummy-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
