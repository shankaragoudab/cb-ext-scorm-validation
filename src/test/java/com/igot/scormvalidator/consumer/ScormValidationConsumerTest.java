package com.igot.scormvalidator.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.igot.scormvalidator.scorm.ScormPackageProcessor;
import com.igot.scormvalidator.scorm.model.RewireResult;
import com.igot.scormvalidator.scorm.model.ScormProcessingResult;
import com.igot.scormvalidator.scorm.model.ValidationResult;
import com.igot.scormvalidator.storage.service.StorageService;
import com.igot.scormvalidator.util.Constants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Kafka event body only carries identifiers (contentId/resourceId/validationId/trace info) —
 * the producer no longer pushes the full tracking record (see {@code ScormValidationServiceImpl}).
 * So every test that gets past the initial "in progress" update stubs
 * {@code cassandraOperation.getRecordsByProperties(...)} to hand back the tracking record
 * (crucially, the {@code artifactUrl}) the consumer re-reads before it can download anything.
 * Both directions go through the plain {@link StorageService} interface, backed by the
 * jclouds/cloud-store-sdk GCS client (see {@code StorageServiceImpl}).
 */
@ExtendWith(MockitoExtension.class)
class ScormValidationConsumerTest {

    private static final String RESOURCE_ID = "resource-1";
    private static final String CONTENT_ID = "content-1";
    private static final String EVENT = "{\"resourceId\":\"" + RESOURCE_ID + "\",\"contentId\":\"" + CONTENT_ID
            + "\",\"validationId\":\"v-1\"}";
    private static final String ARTIFACT_URL =
            "https://storage.googleapis.com/igot/content/do_123/artifact/do_123_v1.zip";

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private StorageService storageService;

    @Mock
    private ScormPackageProcessor scormPackageProcessor;

    private ScormValidationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ScormValidationConsumer(
                cassandraOperation, storageService, scormPackageProcessor, new ObjectMapper());
    }

    @Test
    void processScormValidationRequestedDoesNothingForBlankMessage() {
        consumer.processScormValidationRequested(new ConsumerRecord<>("topic", 0, 0L, "key", ""));

        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void processScormValidationRequestedDispatchesProcessingAsynchronously() throws IOException {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());
        stubTrackingRecord(ARTIFACT_URL);
        stubDownloadAndValidOutcome();

        consumer.processScormValidationRequested(new ConsumerRecord<>("topic", 0, 0L, RESOURCE_ID, EVENT));

        verify(cassandraOperation, timeout(2000).times(3)).updateRecord(anyString(), anyString(), anyMap(), anyMap());
    }

    @Test
    void initiateScormValidationProcessDoesNothingForUnparsableJson() {
        consumer.initiateScormValidationProcess("not-json");

        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void initiateScormValidationProcessDoesNothingWhenResourceIdMissing() {
        consumer.initiateScormValidationProcess("{\"validationId\":\"v-1\",\"contentId\":\"" + CONTENT_ID + "\"}");

        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void initiateScormValidationProcessDoesNothingWhenContentIdMissing() {
        consumer.initiateScormValidationProcess(
                "{\"resourceId\":\"" + RESOURCE_ID + "\",\"validationId\":\"v-1\"}");

        verifyNoInteractions(cassandraOperation);
    }

    @Test
    void initiateScormValidationProcessMarksFailedWhenTrackingRecordNotFound() {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());
        when(cassandraOperation.getRecordsByProperties(anyString(), anyString(), anyMap(), any(), anyInt()))
                .thenReturn(Collections.emptyList());

        consumer.initiateScormValidationProcess(EVENT);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(2)).updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_IN_PROGRESS, updates.get(0).get(Constants.STATUS));
        assertEquals(Constants.STATUS_FAILED, updates.get(1).get(Constants.STATUS));
        assertTrue(((String) updates.get(1).get(Constants.ERROR_REASON)).contains("No SCORM validation tracking record"));
    }

    @Test
    void initiateScormValidationProcessMarksFailedWhenArtifactUrlMissingOnRecord() {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());
        stubTrackingRecord(null);

        consumer.initiateScormValidationProcess(EVENT);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(2)).updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_IN_PROGRESS, updates.get(0).get(Constants.STATUS));
        assertEquals(Constants.STATUS_FAILED, updates.get(1).get(Constants.STATUS));
        assertTrue(((String) updates.get(1).get(Constants.ERROR_REASON)).contains("artifactUrl"));
    }

    @Test
    void initiateScormValidationProcessMarksFailedWhenUpdateThrows() {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap()))
                .thenThrow(new RuntimeException("cassandra down"))
                .thenReturn(successResponse());

        consumer.initiateScormValidationProcess(EVENT);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(2)).updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_IN_PROGRESS, updates.get(0).get(Constants.STATUS));
        assertEquals(Constants.STATUS_FAILED, updates.get(1).get(Constants.STATUS));
        assertEquals("cassandra down", updates.get(1).get(Constants.ERROR_REASON));
    }

    @Test
    void initiateScormValidationProcessMarksFailedWhenDownloadThrows() throws IOException {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());
        stubTrackingRecord(ARTIFACT_URL);
        when(storageService.downloadFile(anyString(), anyString())).thenThrow(new IOException("download failed"));

        consumer.initiateScormValidationProcess(EVENT);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(2)).updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_IN_PROGRESS, updates.get(0).get(Constants.STATUS));
        assertEquals(Constants.STATUS_FAILED, updates.get(1).get(Constants.STATUS));
        assertEquals("download failed", updates.get(1).get(Constants.ERROR_REASON));

        // The container/object key must be parsed straight out of the artifactUrl path, e.g.
        // https://storage.googleapis.com/igot/content/do_123/artifact/do_123_v1.zip ->
        // container "igot", key "content/do_123/artifact/do_123_v1.zip".
        verify(storageService).downloadFile("content/do_123/artifact/do_123_v1.zip", "igot");
    }

    @Test
    void initiateScormValidationProcessTransitionsThroughInProgressValidCompleted() throws IOException {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());
        stubTrackingRecord(ARTIFACT_URL);
        stubDownloadAndValidOutcome();

        consumer.initiateScormValidationProcess(EVENT);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(3)).updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_IN_PROGRESS, updates.get(0).get(Constants.STATUS));
        assertEquals(Constants.STATUS_VALID, updates.get(1).get(Constants.STATUS));
        assertTrue(updates.get(1).containsKey(Constants.VALIDATION_DETAILS));
        assertEquals(Constants.STATUS_COMPLETED, updates.get(2).get(Constants.STATUS));
    }

    @Test
    void initiateScormValidationProcessUsesCompositeContentIdResourceIdCassandraKey() throws IOException {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());
        stubTrackingRecord(ARTIFACT_URL);
        stubDownloadAndValidOutcome();

        consumer.initiateScormValidationProcess(EVENT);

        ArgumentCaptor<Map<String, Object>> fetchKeyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation).getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_SCORM_VALIDATION_STATUS), fetchKeyCaptor.capture(), any(), eq(1));
        assertEquals(CONTENT_ID, fetchKeyCaptor.getValue().get(Constants.CONTENT_ID));
        assertEquals(RESOURCE_ID, fetchKeyCaptor.getValue().get(Constants.RESOURCE_ID));

        ArgumentCaptor<Map<String, Object>> updateKeyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(3)).updateRecord(anyString(), anyString(), anyMap(), updateKeyCaptor.capture());
        for (Map<String, Object> keyMap : updateKeyCaptor.getAllValues()) {
            assertEquals(CONTENT_ID, keyMap.get(Constants.CONTENT_ID));
            assertEquals(RESOURCE_ID, keyMap.get(Constants.RESOURCE_ID));
        }
    }

    @Test
    void initiateScormValidationProcessReplacesOriginalArtifactInPlaceAndUpdatesRecord() throws IOException {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());
        stubTrackingRecord(ARTIFACT_URL);

        File downloaded = Files.createTempFile("consumer-test-download-", ".zip").toFile();
        when(storageService.downloadFile(anyString(), anyString())).thenReturn(downloaded);

        File rewiredZip = Files.createTempFile("consumer-test-rewired-", ".zip").toFile();
        ScormProcessingResult enhancedResult = ScormProcessingResult.builder()
                .outcome(ScormProcessingResult.Outcome.ENHANCED)
                .initialValidation(ValidationResult.builder().valid(false).build())
                .rewireResult(RewireResult.builder()
                        .success(true)
                        .finalValidation(ValidationResult.builder().valid(true).build())
                        .build())
                .rewiredZipFile(rewiredZip)
                .build();
        when(scormPackageProcessor.process(any(File.class))).thenReturn(enhancedResult);
        when(storageService.replaceFile(any(File.class), anyString(), anyString()))
                .thenReturn(ARTIFACT_URL);

        consumer.initiateScormValidationProcess(EVENT);

        // Replaced at the exact same object key the original artifact lived at (not a new
        // filename) — both download and replace go through StorageService.
        verify(storageService).replaceFile(rewiredZip, "content/do_123/artifact/do_123_v1.zip", "igot");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(3)).updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_IN_PROGRESS, updates.get(0).get(Constants.STATUS));
        assertEquals(Constants.STATUS_ENHANCED, updates.get(1).get(Constants.STATUS));
        assertEquals(Boolean.TRUE, updates.get(1).get(Constants.IS_ENHANCED));
        // Replacing in place means the "enhanced" URL is expected to match the original
        // artifactUrl — no new object/filename was created.
        assertEquals(ARTIFACT_URL, updates.get(1).get(Constants.ENHANCED_ARTIFACT_URL));
        assertTrue(updates.get(1).containsKey(Constants.VALIDATION_DETAILS));
        assertEquals(Constants.STATUS_COMPLETED, updates.get(2).get(Constants.STATUS));

        assertFalse(rewiredZip.exists(), "rewired temp zip should be deleted after upload");
    }

    @Test
    void initiateScormValidationProcessClearsStaleErrorReasonOnSuccess() throws IOException {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());
        // Simulate a tracking record that already carries an errorReason left over from an
        // earlier failed attempt on this same (contentId, resourceId) row.
        stubTrackingRecordWithErrorReason(ARTIFACT_URL, "Unable to read package: some earlier failure");
        stubDownloadAndValidOutcome();

        consumer.initiateScormValidationProcess(EVENT);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(3)).updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_VALID, updates.get(1).get(Constants.STATUS));
        assertTrue(updates.get(1).containsKey(Constants.ERROR_REASON),
                "VALID transition must explicitly clear errorReason, not merely omit it");
        assertNull(updates.get(1).get(Constants.ERROR_REASON));

        assertEquals(Constants.STATUS_COMPLETED, updates.get(2).get(Constants.STATUS));
        assertTrue(updates.get(2).containsKey(Constants.ERROR_REASON),
                "COMPLETED transition must explicitly clear errorReason, not merely omit it");
        assertNull(updates.get(2).get(Constants.ERROR_REASON));
    }

    @Test
    void initiateScormValidationProcessFailurePathProducesCompactErrorReasonWithoutRawTempPath() throws IOException {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());
        stubTrackingRecord(ARTIFACT_URL);
        // Mirrors the real-world message shape ScormPackageProcessor produces on a download/read
        // failure: a host-specific, ephemeral local temp path embedded alongside the actual cause.
        String rawFailureMessage = "Unable to read package: /tmp/scorm-download-14694459477462008527/"
                + "do_114629015851753472134_1785773210347_valid-scorm.zip (No such file or directory)";
        when(storageService.downloadFile(anyString(), anyString())).thenThrow(new IOException(rawFailureMessage));

        consumer.initiateScormValidationProcess(EVENT);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(2)).updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_FAILED, updates.get(1).get(Constants.STATUS));
        String errorReason = (String) updates.get(1).get(Constants.ERROR_REASON);
        assertNotNull(errorReason);
        assertFalse(errorReason.contains("/tmp/"), "errorReason must not leak the ephemeral local temp-file path");
        assertTrue(errorReason.contains("No such file or directory"),
                "errorReason must keep the legitimately useful failure context");
        assertTrue(errorReason.length() <= 500, "errorReason must stay within the compact length bound");
    }

    @Test
    void initiateScormValidationProcessMarksInvalidWithSummaryAndDetails() throws IOException {
        when(cassandraOperation.updateRecord(anyString(), anyString(), anyMap(), anyMap())).thenReturn(successResponse());
        stubTrackingRecord(ARTIFACT_URL);

        File downloaded = Files.createTempFile("consumer-test-download-", ".zip").toFile();
        when(storageService.downloadFile(anyString(), anyString())).thenReturn(downloaded);

        ScormProcessingResult invalidResult = ScormProcessingResult.builder()
                .outcome(ScormProcessingResult.Outcome.INVALID)
                .initialValidation(ValidationResult.builder().valid(false)
                        .errors(List.of("No imsmanifest.xml found in the package"))
                        .build())
                .rewireResult(RewireResult.builder()
                        .success(false)
                        .finalValidation(ValidationResult.builder().valid(false)
                                .errors(List.of("No imsmanifest.xml found and no HTML launch file exists anywhere "
                                        + "in the package; cannot auto-generate a manifest"))
                                .build())
                        .build())
                .build();
        when(scormPackageProcessor.process(any(File.class))).thenReturn(invalidResult);

        consumer.initiateScormValidationProcess(EVENT);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(cassandraOperation, times(2)).updateRecord(anyString(), anyString(), captor.capture(), anyMap());
        List<Map<String, Object>> updates = captor.getAllValues();

        assertEquals(Constants.STATUS_IN_PROGRESS, updates.get(0).get(Constants.STATUS));
        assertEquals(Constants.STATUS_INVALID, updates.get(1).get(Constants.STATUS));
        assertTrue(((String) updates.get(1).get(Constants.ERROR_REASON)).contains("cannot auto-generate a manifest"));
        assertTrue(updates.get(1).containsKey(Constants.VALIDATION_DETAILS));
    }

    private void stubTrackingRecord(String artifactUrl) {
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.CONTENT_ID, CONTENT_ID);
        record.put(Constants.RESOURCE_ID, RESOURCE_ID);
        if (artifactUrl != null) {
            record.put(Constants.ARTIFACT_URL, artifactUrl);
        }
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_SCORM_VALIDATION_STATUS), anyMap(), any(), eq(1)))
                .thenReturn(List.of(record));
    }

    /**
     * Same as {@link #stubTrackingRecord(String)} but with a pre-existing errorReason column, to
     * simulate a row left over from an earlier failed attempt on this same tracking record.
     */
    private void stubTrackingRecordWithErrorReason(String artifactUrl, String existingErrorReason) {
        Map<String, Object> record = new HashMap<>();
        record.put(Constants.CONTENT_ID, CONTENT_ID);
        record.put(Constants.RESOURCE_ID, RESOURCE_ID);
        record.put(Constants.ARTIFACT_URL, artifactUrl);
        record.put(Constants.ERROR_REASON, existingErrorReason);
        when(cassandraOperation.getRecordsByProperties(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_SCORM_VALIDATION_STATUS), anyMap(), any(), eq(1)))
                .thenReturn(List.of(record));
    }

    private void stubDownloadAndValidOutcome() throws IOException {
        File downloaded = Files.createTempFile("consumer-test-download-", ".zip").toFile();
        when(storageService.downloadFile(anyString(), anyString())).thenReturn(downloaded);

        ScormProcessingResult validResult = ScormProcessingResult.builder()
                .outcome(ScormProcessingResult.Outcome.VALID)
                .initialValidation(ValidationResult.builder().valid(true).build())
                .build();
        when(scormPackageProcessor.process(any(File.class))).thenReturn(validResult);
    }

    private Map<String, Object> successResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE, Constants.SUCCESS);
        return response;
    }
}
