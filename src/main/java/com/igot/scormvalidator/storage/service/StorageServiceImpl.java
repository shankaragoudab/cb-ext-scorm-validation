package com.igot.scormvalidator.storage.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.sunbird.cloud.storage.BaseStorageService;
import org.sunbird.cloud.storage.factory.StorageConfig;
import org.sunbird.cloud.storage.factory.StorageServiceFactory;
import scala.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * {@link StorageService}: talks to cloud storage via the jclouds-backed cloud-store-sdk, using an
 * explicit service-account credential (cloud.storage.key/secret), matching the pattern used by
 * sunbird-cb-ext's and knowledge-platform's own storage service implementations.
 */
@Service
public class StorageServiceImpl implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(StorageServiceImpl.class.getName());

    @Value("${cloud.storage.type.name}")
    private String cloudStorageTypeName;

    @Value("${cloud.storage.key}")
    private String cloudStorageKey;

    @Value("${cloud.storage.secret}")
    private String cloudStorageSecret;

    @Value("${cloud.storage.endpoint}")
    private String cloudStorageEndpoint;

    private BaseStorageService storageService;

    @PostConstruct
    public void init() {
        storageService = StorageServiceFactory.getStorageService(new StorageConfig(
                cloudStorageTypeName,
                cloudStorageKey,
                cloudStorageSecret.replace("\\n", "\n"),
                Option.apply(cloudStorageEndpoint),
                Option.empty()));
    }

    @Override
    public String uploadFile(File file, String cloudFolderName, String containerName) throws IOException {
        String objectKey = cloudFolderName + "/" + file.getName();
        return upload(file, objectKey, containerName);
    }

    @Override
    public String replaceFile(File file, String objectKey, String containerName) throws IOException {
        return upload(file, objectKey, containerName);
    }

    /** Cloud object stores overwrite whatever is at objectKey, so upload and replace share this. */
    private String upload(File file, String objectKey, String containerName) throws IOException {
        try {
            return storageService.upload(containerName, file.getAbsolutePath(), objectKey,
                    Option.apply(false), Option.apply(1), Option.apply(5), Option.empty());
        } catch (Exception e) {
            logger.error("StorageServiceImpl:upload: exception uploading {}", objectKey, e);
            throw new IOException("Failed to upload file to cloud storage: " + objectKey, e);
        }
    }

    @Override
    public File downloadFile(String objectKey, String containerName) throws IOException {
        try {
            File tempDir = Files.createTempDirectory("scorm-download-").toFile();
            // cloud-store-sdk concatenates localPath + fileName with no separator, so localPath
            // must already end with a trailing slash.
            String localPath = tempDir.getAbsolutePath() + File.separator;
            storageService.download(containerName, objectKey, localPath, Option.apply(Boolean.FALSE));
            return new File(tempDir, new File(objectKey).getName());
        } catch (Exception e) {
            logger.error("StorageServiceImpl:downloadFile: exception downloading {}", objectKey, e);
            throw new IOException("Failed to download file from cloud storage: " + objectKey, e);
        }
    }
}
