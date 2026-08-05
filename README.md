# cb-ext-scorm-validation

Spring Boot microservice that validates and tracks the status of uploaded SCORM course packages for the iGOT platform.

It exposes two REST endpoints under `/scorm/v1` (see `ScormValidationController`).

## How it works

1. A client calls `POST /scorm/v1/validate` for a `(contentId, resourceId)` pair. The service verifies `resourceId` belongs to `contentId`'s `leafNodes` (via the Content Service), checks its `mimeType` against the configured supported list, and resolves its `artifactUrl`. It then writes a tracking record to Cassandra with status `STARTED` and publishes a `SCORM_VALIDATION_REQUESTED` event to Kafka.
2. A Kafka consumer (`ScormValidationConsumer`) picks up the event asynchronously, downloads the artifact, and walks the record through `IN_PROGRESS` → `VALID`/`ENHANCED`/`INVALID` → `COMPLETED` (invalid packages stop at `INVALID` and aren't marked `COMPLETED`), updating Cassandra at each step. Package parsing/validation (`imsmanifest.xml`, tracking-call detection) and the enhancement/rewire routine are implemented in `ScormPackageProcessor` / `ScormValidationEngine` / `ScormRewireEngine`.
3. A client can poll `POST /scorm/v1/status` for the current status of a `contentId`, optionally narrowed to one `resourceId`, at any point.

## Prerequisites

- Java 17
- A running Cassandra cluster
- A running Kafka broker
- Network access to the iGOT Content Service (for reading `leafNodes`/`mimeType`/`artifactUrl` by id)
- Cloud storage credentials (Google Cloud Storage by default, via the jclouds-backed `cloud-store-sdk`) for the SCORM package container

## Configuration

All configuration lives in `src/main/resources/application.properties`:

| Group | Properties | Notes |
|---|---|---|
| Cassandra | `cassandra.config.host`, `sunbird_cassandra_consistency_level` | Connection is opened by `common-util`'s `CassandraConnectionManagerImpl` (raw DataStax driver, not Spring Data Cassandra) |
| Kafka | `spring.kafka.bootstrap-servers`, `kafka.scorm.validation.request.topic(.group)` | Request/consumer topic for the async validation pipeline |
| Auth | `sso.url`, `sso.realm`, `accesstoken.publickey.basepath` | JWT verification, handled by `common-util`'s `AccessTokenValidator`/`KeyManager`. Callers must send the token in the `x-authenticated-user-token` header |
| Cloud storage | `cloud.storage.type.name`, `cloud.storage.key`, `cloud.storage.secret`, `cloud.storage.endpoint`, `scorm.validation.container.name` | Where uploaded SCORM packages live; talks to the store directly via `cloud-store-sdk` (`StorageServiceImpl`) |
| Content Service | `content-service-host`, `content-read-endpoint` | Used to resolve a `contentId`/`resourceId` to its `leafNodes`/`mimeType`/`artifactUrl` |
| SCORM validation | `scorm.validation.supported.mime-types` | Comma-separated list of mime types accepted for the `resourceId` being validated (e.g. `application/vnd.ekstep.html-archive`) |

Both Cassandra and the auth beans are provided by `net.karmayogibharat:common-util`, auto-configured via its `CommonAutoConfiguration`.

**Security note:** `application.properties` currently holds a live GCP service-account private key (`cloud.storage.secret`) in plaintext, checked into git. This should be rotated and moved to a secret store rather than version control before this is used outside local development.

## Connecting to Cassandra

The service connects to whatever host is set in `cassandra.config.host` (default `localhost`, port `9042`).

`common-util`'s `CassandraConnectionManagerImpl` hardcodes the driver's local datacenter to `datacenter1` — this is the default datacenter name for a fresh single-node Cassandra install, so no extra config is needed locally. (The `spring.cassandra.local-datacenter=DC1` property in `application.properties` is a leftover from Spring Data Cassandra's own auto-configuration, which this project excludes — it has no effect and can be ignored/removed.)

Connect with `cqlsh` to verify or run schema changes:

```sh
cqlsh localhost 9042
```

Then create the keyspace/table described below.

### Schema

No migration tooling is wired up (matching the rest of the platform's services) — run this once against the target cluster before first use:

```sql
CREATE KEYSPACE IF NOT EXISTS sunbird
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE sunbird.scorm_validation_status (
    contentid           text,
    resourceid          text,
    validationid        text,
    filename            text,
    artifacturl         text,
    enhancedartifacturl text,
    status              text,
    isenhanced          boolean,
    errorreason         text,
    validationdetails   text,
    requestedby         text,
    createdat           text,
    updatedat           text,
    retrycount          int,
    PRIMARY KEY (contentid, resourceid)
);
```

`contentid` is the partition key and `resourceid` the clustering key — this lets `POST /scorm/v1/status` look up either a single `(contentId, resourceId)` row or every `resourceId` row under a `contentId`.

`createdat`/`updatedat` are `text` (ISO-8601, e.g. `2026-07-30T09:20:30.441Z`), not `timestamp` — the DataStax driver's default codec registry (used by `common-util`'s `CassandraOperation`) only round-trips `java.time.Instant` for CQL `timestamp` columns, and this project stores the formatted string instead to avoid a codec mismatch.

## Kafka setup

The service both publishes to and consumes from `kafka.scorm.validation.request.topic` (default `scorm.validation.request`), pointed at `spring.kafka.bootstrap-servers` (default `localhost:9092`).

Create the topic before first use if your broker doesn't auto-create topics:

```sh
kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic scorm.validation.request \
  --partitions 1 \
  --replication-factor 1
```

The consumer group is `kafka.scorm.validation.request.topic.group` (default `scormvalidator`).

## API

Both endpoints require the `x-authenticated-user-token` header and return the same envelope shape: `{"id", "ver", "ts", "responseCode", "result"}`.

### `POST /scorm/v1/validate`

Request body nests `contentId`/`resourceId` under `request`:

```json
{
  "request": {
    "contentId": "<contentId>",
    "resourceId": "<resourceId>"
  }
}
```

Success response (`202 Accepted`):

```json
{
  "result": {
    "status": "STARTED",
    "validationId": "...",
    "contentId": "<contentId>",
    "resourceId": "<resourceId>",
    "createdAt": "2026-08-04T10:00:00.000Z"
  }
}
```

### `POST /scorm/v1/status`

Request body is flat (no `request` wrapper), and `resourceId` is optional:

```json
{
  "contentId": "<contentId>",
  "resourceId": "<resourceId>"
}
```

Response always returns a `resources` array — one element for a `(contentId, resourceId)` lookup, all matching rows for a `contentId`-only lookup:

```json
{
  "result": {
    "status": "success",
    "contentId": "<contentId>",
    "resources": [
      { "resourceId": "<resourceId>", "status": "STARTED", "...": "..." }
    ]
  }
}
```

## Running locally

```sh
./mvnw spring-boot:run
```

The service starts on port `7001` (`server.port`).

**JDK 17 note:** `cloud-store-sdk`'s underlying jclouds library predates the Java module system and reflects into `java.base` internals (Guice/cglib bytecode generation, Gson-based deserialization). The pom already adds the required `--add-opens` flags to `maven-surefire-plugin` (for `mvn test`) and `spring-boot-maven-plugin` (for `mvn spring-boot:run`), so command-line builds work out of the box. If you run/debug from an IDE instead, add the same `--add-opens` flags to its run configuration's VM options, or set them via the `JDK_JAVA_OPTIONS` environment variable — otherwise you'll hit `InaccessibleObjectException` the first time storage code runs.

## Status lifecycle

`STARTED` → `IN_PROGRESS` → `VALID`, `ENHANCED`, or `INVALID` → `COMPLETED` (`VALID`/`ENHANCED` proceed to `COMPLETED`; `INVALID` stops there). A processing exception at any point sets `FAILED` with a compact `errorReason` (no raw local file paths, capped length); a later successful retry clears `errorReason` again.

## Tests

```sh
./mvnw test
```
