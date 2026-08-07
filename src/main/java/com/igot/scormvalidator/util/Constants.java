package com.igot.scormvalidator.util;

public class Constants {

    private Constants() {
    }
    public static final String X_AUTH_TOKEN = "x-authenticated-user-token";
    public static final String INVALID_AUTH_TOKEN = "Invalid auth token. Please supply a valid auth token";
    public static final String KEYSPACE_SUNBIRD = "sunbird";
    public static final String TABLE_SCORM_VALIDATION_STATUS = "scorm_validation_status";
    public static final String VALIDATION_ID = "validationId";
    public static final String CONTENT_ID = "contentId";
    public static final String RESOURCE_ID = "resourceId";
    public static final String FILE_NAME = "fileName";
    public static final String ARTIFACT_URL = "artifactUrl";
    public static final String ENHANCED_ARTIFACT_URL = "enhancedArtifactUrl";
    public static final String STATUS = "status";
    public static final String IS_ENHANCED = "isEnhanced";
    public static final String ERROR_REASON = "errorReason";
    public static final String VALIDATION_DETAILS = "validationDetails";
    public static final String REQUESTED_BY = "requestedBy";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String RETRY_COUNT = "retryCount";
    public static final String API_VERSION_1 = "1.0";
    public static final String API_SCORM_VALIDATE = "api.scorm.validate";
    public static final String API_SCORM_VALIDATE_STATUS = "api.scorm.validate.status";
    public static final String CONTENT_SERVICE_HOST = "content-service-host";
    public static final String CONTENT_READ_END_POINT = "content-read-endpoint";
    public static final String RESPONSE_CODE = "responseCode";
    public static final String RESULT = "result";
    public static final String CONTENT = "content";
    public static final String OK = "OK";
    public static final String CHILD_NODES = "childNodes";
    public static final String MIME_TYPE = "mimeType";
    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_ENHANCED = "ENHANCED";
    public static final String STATUS_INVALID = "INVALID";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String EVENT_TYPE = "eventType";
    public static final String EVENT_TYPE_SCORM_VALIDATION_REQUESTED = "SCORM_VALIDATION_REQUESTED";
    public static final String CONTENT_TYPE = "contentType";
    public static final String TRACE_ID = "traceId";
    public static final String RESOURCES = "resources";
    public static final String RESPONSE = "response";
    public static final String SUCCESS = "success";
    public static final String FAILED = "Failed";
    public static final String ERROR_MESSAGE = "errmsg";
    public static final String REQUEST = "request";
    public static final String VALIDATION_NOT_FOUND = "SCORM validation record not found";
    public static final String MISSING_REQUEST_BODY = "request is empty";
    public static final String MISSING_CONTENT_ID = "contentId is required";
    public static final String MISSING_RESOURCE_ID = "resourceId is required";
    public static final String CONTENT_NOT_FOUND = "Content not found for id: ";
    public static final String ARTIFACT_URL_NOT_FOUND = "artifactUrl not available for resourceId: ";
    public static final String RESOURCE_NOT_PART_OF_CONTENT = "resourceId is not part of the content's childNodes: ";
    public static final String UNSUPPORTED_MIME_TYPE = "Unsupported mimeType for resourceId: ";
    public static final String PRIMARY_CATEGORY = "primaryCategory";
    public static final String CONTENT_PATH_SEGMENT = "content/";
    public static final String ARTIFACT_URL_NO_PATH = "artifactUrl has no path to derive an object key from: ";
    public static final String ARTIFACT_URL_NO_CONTENT_SEGMENT = "artifactUrl does not contain a '"
            + CONTENT_PATH_SEGMENT + "' segment to derive the object key from: ";
}
