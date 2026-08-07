package com.igot.scormvalidator.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.scormvalidator.scorm.ScormPackageProcessor;
import com.igot.scormvalidator.scorm.model.ScormProcessingResult;
import com.igot.scormvalidator.scorm.model.ValidationResult;
import com.igot.scormvalidator.storage.service.StorageService;
import com.igot.scormvalidator.util.Constants;
import com.igot.scormvalidator.util.ScormValidatorServerProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.igot.common.cassandra.CassandraOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ScormValidationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ScormValidationConsumer.class);
    private static final int MAX_SUMMARY_MESSAGES = 2;

    /** Hard cap on the errorReason text persisted to Cassandra; it's an API-facing summary, not a debug log. */
    private static final int MAX_ERROR_REASON_LENGTH = 500;

    /** Matches the local temp-download path (host-specific and ephemeral) so it can be stripped from errorReason. */
    private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile("\\S*/tmp/\\S+");

    /** The path segment marking the start of the actual object key inside the configured bucket. */
    private static final String CONTENT_PATH_SEGMENT = "content/";

    private final CassandraOperation cassandraOperation;
    private final StorageService storageService;
    private final ScormPackageProcessor scormPackageProcessor;
    private final ObjectMapper objectMapper;
    private final ScormValidatorServerProperties serverProperties;

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
            event = objectMapper.readValue(message, new TypeReference<Map<String, Object>>() {
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

        String contentId = (String) event.get(Constants.CONTENT_ID);
        if (StringUtils.isBlank(contentId)) {
            logger.error("ScormValidationConsumer: missing contentId in event payload {}", message);
            return;
        }

        TrackingKey key = new TrackingKey(contentId, resourceId);

        try {
            updateStatus(key, Constants.STATUS_IN_PROGRESS, null);

            // The Kafka event only carries identifiers (contentId/resourceId/validationId/trace
            // info) — the producer no longer pushes the full tracking record. Read it back from
            // Cassandra so we always act on the current row (artifactUrl, retry count, etc.).
            Map<String, Object> trackingRecord = fetchTrackingRecord(key);
            String artifactUrl = (String) trackingRecord.get(Constants.ARTIFACT_URL);
            if (StringUtils.isBlank(artifactUrl)) {
                throw new IllegalStateException("artifactUrl missing on tracking record for resourceId " + resourceId);
            }

            ArtifactLocation location = parseArtifactLocation(artifactUrl);
            File localFile = storageService.downloadFile(location.objectKey(), location.container());
            try {
                ScormProcessingResult result = scormPackageProcessor.process(localFile);
                switch (result.getOutcome()) {
                    case VALID -> handleValid(key, result);
                    case ENHANCED -> handleEnhanced(key, result, location);
                    case INVALID -> handleInvalid(key, result);
                }
            } finally {
                deleteQuietly(localFile);
            }
        } catch (Exception e) {
            logger.error("ScormValidationConsumer: processing failed for resourceId {}", resourceId, e);
            updateStatus(key, Constants.STATUS_FAILED, compactErrorReason(e.getMessage()));
        }
    }

    /** Re-reads the tracking record for {@code (contentId, resourceId)} from Cassandra. */
    private Map<String, Object> fetchTrackingRecord(TrackingKey key) {
        Map<String, Object> keyMap = new HashMap<>();
        keyMap.put(Constants.CONTENT_ID, key.contentId());
        keyMap.put(Constants.RESOURCE_ID, key.resourceId());
        List<Map<String, Object>> records = cassandraOperation.getRecordsByProperties(
                Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, keyMap, null, 1);
        if (records.isEmpty()) {
            throw new IllegalStateException("No SCORM validation tracking record found for resourceId " + key.resourceId());
        }
        return records.get(0);
    }

    private void handleValid(TrackingKey key, ScormProcessingResult result) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(Constants.STATUS, Constants.STATUS_VALID);
        attributes.put(Constants.VALIDATION_DETAILS, serialize(result.getInitialValidation()));
        // Clear any stale errorReason left over from an earlier failed attempt.
        attributes.put(Constants.ERROR_REASON, null);
        updateRecord(key, attributes);
        updateStatus(key, Constants.STATUS_COMPLETED, null);
    }

    private void handleEnhanced(TrackingKey key, ScormProcessingResult result, ArtifactLocation location) throws IOException {
        File rewiredZip = result.getRewiredZipFile();
        try {
            // Replace the ORIGINAL artifact object in place (same container, same full object
            // key/filename) rather than uploading under a new name. Cloud object stores overwrite
            // whatever is already at a given key, so the resulting URL is identical to the
            // original artifactUrl — nothing else (e.g. Content Service metadata) needs to change
            // to keep pointing at valid content.
            String enhancedUrl = storageService.replaceFile(rewiredZip, location.objectKey(), location.container());

            Map<String, Object> attributes = new HashMap<>();
            attributes.put(Constants.ENHANCED_ARTIFACT_URL, enhancedUrl);
            attributes.put(Constants.IS_ENHANCED, true);
            attributes.put(Constants.STATUS, Constants.STATUS_ENHANCED);
            attributes.put(Constants.VALIDATION_DETAILS, serialize(result.getRewireResult()));
            // Clear any stale errorReason left over from an earlier failed attempt.
            attributes.put(Constants.ERROR_REASON, null);
            updateRecord(key, attributes);

            // No Content Service update needed: the artifact was replaced at its original URL, so
            // existing content metadata (artifactUrl) stays valid without any change.
            updateStatus(key, Constants.STATUS_COMPLETED, null);
        } finally {
            deleteQuietly(rewiredZip);
        }
    }

    private void handleInvalid(TrackingKey key, ScormProcessingResult result) {
        ValidationResult reported = result.getRewireResult() != null
                ? result.getRewireResult().getFinalValidation()
                : result.getInitialValidation();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(Constants.STATUS, Constants.STATUS_INVALID);
        attributes.put(Constants.ERROR_REASON, buildErrorSummary(reported));
        attributes.put(Constants.VALIDATION_DETAILS, serialize(reported));
        updateRecord(key, attributes);
    }

    private String buildErrorSummary(ValidationResult validation) {
        if (validation == null) {
            return "SCORM package failed validation";
        }
        List<String> messages = new ArrayList<>();
        if (validation.getErrors() != null) {
            messages.addAll(validation.getErrors());
        }
        if (validation.getMissingFiles() != null) {
            messages.addAll(validation.getMissingFiles());
        }
        if (messages.isEmpty()) {
            return "SCORM package failed validation";
        }
        String summary = messages.stream().limit(MAX_SUMMARY_MESSAGES).collect(Collectors.joining("; "));
        return compactErrorReason(summary);
    }

    /** Strips local temp-file paths and truncates to {@link #MAX_ERROR_REASON_LENGTH}. */
    private String compactErrorReason(String rawMessage) {
        if (StringUtils.isBlank(rawMessage)) {
            return "SCORM validation failed";
        }
        String compacted = ABSOLUTE_PATH_PATTERN.matcher(rawMessage).replaceAll("the package file").trim();
        compacted = compacted.replaceAll("\\s{2,}", " ").trim();
        if (compacted.isEmpty()) {
            compacted = "SCORM validation failed";
        }
        if (compacted.length() > MAX_ERROR_REASON_LENGTH) {
            compacted = compacted.substring(0, MAX_ERROR_REASON_LENGTH - 3) + "...";
        }
        return compacted;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            logger.error("ScormValidationConsumer: failed to serialize validation detail", e);
            return String.valueOf(value);
        }
    }

    /**
     * Resolves an artifact URL to a cloud-storage location. The URL's host/leading path segments
     * (e.g. a CDN domain, a proxy path like {@code content-store}) are not the actual bucket —
     * the real bucket is the configured {@code scorm.validation.container.name}. The object key
     * is the URL path starting from its {@code content/} segment, since that's how objects are
     * actually laid out in the bucket (e.g. {@code content/<contentId>/artifact/<file>.zip}).
     */
    private ArtifactLocation parseArtifactLocation(String artifactUrl) {
        String path = artifactUrl;
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }

        String uriPath;
        try {
            uriPath = URI.create(path).getPath();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse artifactUrl: " + artifactUrl, e);
        }
        if (StringUtils.isBlank(uriPath)) {
            throw new IllegalStateException("artifactUrl has no path to derive an object key from: " + artifactUrl);
        }

        int contentIdx = uriPath.indexOf(CONTENT_PATH_SEGMENT);
        if (contentIdx < 0) {
            throw new IllegalStateException("artifactUrl does not contain a '" + CONTENT_PATH_SEGMENT
                    + "' segment to derive the object key from: " + artifactUrl);
        }
        String objectKey = uriPath.substring(contentIdx);
        return new ArtifactLocation(serverProperties.getScormValidationContainerName(), objectKey);
    }

    /** A resolved cloud-storage location: bucket/container name plus the full object key. */
    private record ArtifactLocation(String container, String objectKey) {
    }

    /** The composite Cassandra key for a tracking row: contentId (partition) + resourceId (clustering). */
    private record TrackingKey(String contentId, String resourceId) {
    }

    private void deleteQuietly(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            logger.warn("ScormValidationConsumer: failed to delete local temp file {}", file.getAbsolutePath(), e);
        }
    }

    private void updateStatus(TrackingKey key, String status, String errorReason) {
        Map<String, Object> updateAttributes = new HashMap<>();
        updateAttributes.put(Constants.STATUS, status);
        // Always set explicitly (including null) so a successful retry clears a stale errorReason.
        updateAttributes.put(Constants.ERROR_REASON, errorReason);
        updateRecord(key, updateAttributes);
    }

    private void updateRecord(TrackingKey key, Map<String, Object> updateAttributes) {
        updateAttributes.put(Constants.UPDATED_AT, Instant.now().toString());

        Map<String, Object> keyMap = new HashMap<>();
        keyMap.put(Constants.CONTENT_ID, key.contentId());
        keyMap.put(Constants.RESOURCE_ID, key.resourceId());

        Map<String, Object> response = cassandraOperation.updateRecord(
                Constants.KEYSPACE_SUNBIRD, Constants.TABLE_SCORM_VALIDATION_STATUS, updateAttributes, keyMap);
        if (!Constants.SUCCESS.equalsIgnoreCase((String) response.get(Constants.RESPONSE))) {
            logger.error("ScormValidationConsumer: failed to update record for contentId={} resourceId={} attributes={}",
                    key.contentId(), key.resourceId(), updateAttributes.keySet());
        }
    }
}
