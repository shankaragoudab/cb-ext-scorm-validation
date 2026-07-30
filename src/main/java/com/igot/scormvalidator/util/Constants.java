package com.igot.scormvalidator.util;

public class Constants {

    private Constants() {
    }

    // Auth
    public static final String DOT_SEPARATOR = ".";
    public static final String SHA_256_WITH_RSA = "SHA256withRSA";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String SUB = "sub";
    public static final String SSO_URL = "sso.url";
    public static final String SSO_REALM = "sso.realm";
    public static final String ACCESS_TOKEN_PUBLICKEY_BASEPATH = "accesstoken.publickey.basepath";
    public static final String X_AUTH_TOKEN = "x-authenticated-user-token";
    public static final String INVALID_AUTH_TOKEN = "Invalid auth token. Please supply a valid auth token";

    // Cassandra connection (read via PropertiesCache from cassandra.config.properties/application.properties)
    public static final String CASSANDRA_CONFIG_HOST = "cassandra.config.host";
    public static final String CORE_CONNECTIONS_PER_HOST_FOR_LOCAL = "coreConnectionsPerHostForLocal";
    public static final String CORE_CONNECTIONS_PER_HOST_FOR_REMOTE = "coreConnectionsPerHostForRemote";
    public static final String HEARTBEAT_INTERVAL = "heartbeatIntervalSeconds";
    public static final String SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL = "sunbird_cassandra_consistency_level";
    public static final String DEFAULT_SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL = "ONE";
    public static final String LOCAL_DATACENTER = "datacenter1";
    public static final String ERROR = "ERROR";

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

    // CassandraOperation / CassandraUtil query-building tokens
    public static final String INSERT_INTO = "INSERT INTO ";
    public static final String OPEN_BRACE = "(";
    public static final String VALUES_WITH_BRACE = ") VALUES (";
    public static final String QUE_MARK = "?";
    public static final String COMMA = ",";
    public static final String CLOSING_BRACE = ");";
    public static final String SEMICOLON = ";";

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
