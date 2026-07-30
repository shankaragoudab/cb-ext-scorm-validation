package com.igot.scormvalidator.util;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized {@code @Value}-backed configuration, mirroring
 * org.sunbird.common.util.CbExtServerProperties, so property keys live in one
 * place instead of being scattered across services.
 */
@Component
@Getter
public class ScormValidatorServerProperties {

    @Value("${kafka.scorm.validation.request.topic}")
    private String scormValidationRequestedTopic;

    @Value("${content-service-host}")
    private String contentServiceHost;

    @Value("${content-read-endpoint}")
    private String contentReadEndpoint;
}
