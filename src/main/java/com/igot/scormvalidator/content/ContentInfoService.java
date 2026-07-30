package com.igot.scormvalidator.content;

import com.igot.scormvalidator.util.Constants;
import com.igot.scormvalidator.util.ScormValidatorServerProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Map;

/**
 * Reads content metadata (including artifactUrl) from the Content Service by resourceId,
 * mirroring ContentInfoServiceImpl.readContentFromService in cb-ext-course-service.
 */
@Service
@RequiredArgsConstructor
public class ContentInfoService {

    private static final Logger logger = LoggerFactory.getLogger(ContentInfoService.class);

    private final RestTemplate restTemplate;
    private final ScormValidatorServerProperties serverProperties;

    @SuppressWarnings("unchecked")
    public Map<String, Object> readContent(String contentId) {
        if (StringUtils.isBlank(contentId)) {
            return Collections.emptyMap();
        }
        String url = serverProperties.getContentServiceHost() + serverProperties.getContentReadEndpoint() + "/" + contentId;
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response != null && Constants.OK.equalsIgnoreCase((String) response.get(Constants.RESPONSE_CODE))) {
                Map<String, Object> result = (Map<String, Object>) response.get(Constants.RESULT);
                if (result != null) {
                    Map<String, Object> content = (Map<String, Object>) result.get(Constants.CONTENT);
                    if (content != null) {
                        return content;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("ContentInfoService: failed to read content for resourceId {}", contentId, e);
        }
        return Collections.emptyMap();
    }
}
