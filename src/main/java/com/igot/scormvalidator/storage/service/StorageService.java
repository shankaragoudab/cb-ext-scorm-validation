package com.igot.scormvalidator.storage.service;

import java.io.File;
import java.io.IOException;

public interface StorageService {

    /**
     * Uploads a local file to cloud storage under the given container/folder, keyed by the local
     * file's own name (i.e. lands at {@code cloudFolderName/file.getName()}). This always
     * produces a new object key — use {@link #replaceFile} instead when the goal is to overwrite
     * an existing object without changing its key/URL.
     *
     * @return the cloud URL of the uploaded object.
     */
    String uploadFile(File file, String cloudFolderName, String containerName) throws IOException;

    /**
     * Uploads {@code file} to the exact {@code objectKey} given, overwriting whatever object is
     * already there. Use this to replace an existing artifact in place — the resulting URL is
     * identical to the original, so nothing that references the old URL (e.g. content metadata)
     * needs to be updated.
     *
     * @return the cloud URL of the (overwritten) object — expected to match the original URL.
     */
    String replaceFile(File file, String objectKey, String containerName) throws IOException;

    /**
     * Downloads an object from cloud storage to a local temp file.
     *
     * @return the downloaded local File.
     */
    File downloadFile(String objectKey, String containerName) throws IOException;
}
