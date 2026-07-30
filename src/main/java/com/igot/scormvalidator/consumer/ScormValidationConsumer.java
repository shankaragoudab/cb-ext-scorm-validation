package com.igot.scormvalidator.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.scormvalidator.util.Constants;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.igot.common.cassandra.CassandraOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class ScormValidationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ScormValidationConsumer.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final CassandraOperation cassandraOperation;

    @KafkaListener(topics = "${kafka.scorm.validation.request.topic}",
            groupId = "${kafka.scorm.validation.request.topic.group}")
    public void processScormValidationRequested(ConsumerRecord<String, String> data) {
        if (StringUtils.isBlank(data.value())) {
            logger.error("ScormValidationConsumer:: processScormValidationRequested: Invalid Kafka Msg");
            return;
        }
        logger.info("ScormValidationConsumer:: processScormValidationRequested: Received event to initiate SCORM validation");
        CompletableFuture.runAsync(() -> initiateScormValidationProcess(data.value()));
    }

    void initiateScormValidationProcess(String message) {
        Map<String, Object> event;
        try {
            event = mapper.readValue(message, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            logger.error("ScormValidationConsumer: failed to parse event payload {}", message, e);
            return;
        }

        String resourceId = (String) event.get(Constants.RESOURCE_ID);
        if (StringUtils.isBlank(resourceId)) {
            logger.error("ScormValidationConsumer: missing resourceId in event payload {}", message);
            return;
        }

        try {
            updateStatus(resourceId, Constants.STATUS_IN_PROGRESS, null);
            boolean isValidScorm = isValidScorm(event);
            if (isValidScorm) {
                updateStatus(resourceId, Constants.STATUS_VALID, null);
                updateStatus(resourceId, Constants.STATUS_COMPLETED, null);
            } else {
                // TODO: FR-5 enhancement routine (inject tracking wrapper, re-zip, re-upload,
                // update Resource content URL) — not yet implemented.
                logger.warn("ScormValidationConsumer: resourceId {} requires enhancement; not yet implemented", resourceId);
            }
        } catch (Exception e) {
            logger.error("ScormValidationConsumer: processing failed for resourceId {}", resourceId, e);
            updateStatus(resourceId, Constants.STATUS_FAILED, e.getMessage());
        }
    }

    /**
     * Placeholder for the real SCORM validation routine (imsmanifest.xml parsing,
     * tracking-call presence check). Always reports valid until implemented.
     */
    private boolean isValidScorm(Map<String, Object> event) {
        return true;
    }

    private void updateStatus(String resourceId, String status, String errorReason) {
        Map<String, Object> updateAttributes = new HashMap<>();
        updateAttributes.put(Constants.STATUS, status);
        updateAttributes.put(Constants.UPDATED_AT, Instant.now().toString());
        if (errorReason != null) {
            updateAttributes.put(Constants.ERROR_REASON, errorReason);
        }

        Map<String, Object> keyMap = new HashMap<>();
        keyMap.put(Constants.RESOURCE_ID, resourceId);

        Map<String, Object> response = cassandraOperation.updateRecord(
                Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, updateAttributes, keyMap);
        if (!Constants.SUCCESS.equalsIgnoreCase((String) response.get(Constants.RESPONSE))) {
            logger.error("ScormValidationConsumer: failed to update status={} for resourceId={}", status, resourceId);
        }
    }
}
