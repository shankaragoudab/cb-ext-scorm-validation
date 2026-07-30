package com.igot.scormvalidator.util;

public class Constants {

    private Constants() {
    }

    // Auth
    public static final String X_AUTH_TOKEN = "x-authenticated-user-token";
    public static final String INVALID_AUTH_TOKEN = "Invalid auth token. Please supply a valid auth token";

    // Keyspace / table
    public static final String KEYSPACE_SUNBIRD = "sunbird";
    public static final String TABLE_SCORM_VALIDATION_STATUS = "scorm_validation_status";

    // scorm_validation_status column / map-key names
    public static final String VALIDATION_ID = "validationId";
    public static final String RESOURCE_ID = "resourceId";
    public static final String FILE_NAME = "fileName";
    public static final String ARTIFACT_URL = "artifactUrl";
    public static final String ENHANCED_ARTIFACT_URL = "enhancedArtifactUrl";
    public static final String STATUS = "status";
    public static final String IS_ENHANCED = "isEnhanced";
    public static final String ERROR_REASON = "errorReason";
    public static final String REQUESTED_BY = "requestedBy";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    public static final String RETRY_COUNT = "retryCount";

    // API ids
    public static final String API_VERSION_1 = "1.0";
    public static final String API_SCORM_VALIDATE = "api.scorm.validate";
    public static final String API_SCORM_VALIDATE_STATUS = "api.scorm.validate.status";

    // Content Service (read content metadata by resourceId)
    public static final String CONTENT_SERVICE_HOST = "content-service-host";
    public static final String CONTENT_READ_END_POINT = "content-read-endpoint";
    public static final String RESPONSE_CODE = "responseCode";
    public static final String RESULT = "result";
    public static final String CONTENT = "content";
    public static final String OK = "OK";

    // SCORM validation status values
    public static final String STATUS_STARTED = "STARTED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_ENHANCED = "ENHANCED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    // Kafka
    public static final String EVENT_TYPE = "eventType";
    public static final String EVENT_TYPE_SCORM_VALIDATION_REQUESTED = "SCORM_VALIDATION_REQUESTED";
    public static final String RESOURCE_TYPE = "resourceType";
    public static final String RESOURCE_TYPE_COURSE = "COURSE";
    public static final String TRACE_ID = "traceId";

    // ApiResponse / CassandraOperation result keys
    public static final String RESPONSE = "response";
    public static final String SUCCESS = "success";
    public static final String FAILED = "Failed";
    public static final String ERROR_MESSAGE = "errmsg";

    // Misc
    public static final String VALIDATION_NOT_FOUND = "SCORM validation record not found";
    public static final String MISSING_REQUIRED_FIELDS = "resourceId is required";
    public static final String CONTENT_NOT_FOUND = "Content not found for resourceId: ";
    public static final String ARTIFACT_URL_NOT_FOUND = "artifactUrl not available for resourceId: ";
}
