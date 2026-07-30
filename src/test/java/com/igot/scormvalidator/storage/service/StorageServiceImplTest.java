package com.igot.scormvalidator.storage.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sunbird.cloud.storage.BaseStorageService;
import scala.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * init() builds a real cloud connection via Guice/jclouds and isn't exercised here; @InjectMocks
 * field-injects the mocked BaseStorageService directly into the private storageService field so
 * uploadFile/downloadFile's own logic can be tested in isolation.
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceImplTest {

    @Mock
    private BaseStorageService baseStorageService;

    @InjectMocks
    private StorageServiceImpl storageServiceImpl;

    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("scorm-upload-test-", ".zip");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void uploadFileReturnsUrlFromUnderlyingStorageService() throws IOException {
        when(baseStorageService.upload(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn("https://storage.example.com/uploaded.zip");

        String url = storageServiceImpl.uploadFile(tempFile.toFile(), "scorm", "container");

        assertEquals("https://storage.example.com/uploaded.zip", url);
    }

    @Test
    void uploadFileWrapsUnderlyingFailureAsIOException() {
        when(baseStorageService.upload(anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("upload failed"));

        IOException exception = assertThrows(IOException.class,
                () -> storageServiceImpl.uploadFile(tempFile.toFile(), "scorm", "container"));
        assertTrue(exception.getCause() instanceof RuntimeException);
    }

    @Test
    void downloadFileReturnsFileNamedAfterObjectKeyInsideTempDirectory() throws IOException {
        String objectKey = "scorm/course_1234.zip";

        File downloaded = storageServiceImpl.downloadFile(objectKey, "container");

        assertEquals("course_1234.zip", downloaded.getName());
        assertTrue(downloaded.getParentFile().exists());
    }

    @Test
    void downloadFileWrapsUnderlyingFailureAsIOException() {
        doThrow(new RuntimeException("download failed"))
                .when(baseStorageService).download(anyString(), anyString(), anyString(), any(Option.class));

        IOException exception = assertThrows(IOException.class,
                () -> storageServiceImpl.downloadFile("scorm/course_1234.zip", "container"));
        assertTrue(exception.getCause() instanceof RuntimeException);
    }
}
