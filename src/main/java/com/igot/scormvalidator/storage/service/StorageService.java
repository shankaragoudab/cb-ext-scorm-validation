package com.igot.scormvalidator.storage.service;

import java.io.File;
import java.io.IOException;

public interface StorageService {

    /**
     * Uploads a local file to cloud storage under the given container/folder.
     *
     * @return the cloud URL of the uploaded object.
     */
    String uploadFile(File file, String cloudFolderName, String containerName) throws IOException;

    /**
     * Downloads an object from cloud storage to a local temp file.
     *
     * @return the downloaded local File.
     */
    File downloadFile(String objectKey, String containerName) throws IOException;
}
