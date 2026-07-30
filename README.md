# cb-ext-scorm-validation

Spring Boot microservice that validates and tracks the status of uploaded SCORM course packages for the iGOT platform.

It exposes its functionality as REST APIs under `/v1/scorm/**` (see `ScormValidationController`) — endpoints aren't enumerated in this doc since more will be added over time; refer to the controller for the current list.

## How it works

1. A client requests validation for a `resourceId`. The service looks up the content's `artifactUrl` from the Content Service, writes a tracking record to Cassandra with status `STARTED`, and publishes a `SCORM_VALIDATION_REQUESTED` event to Kafka.
2. A Kafka consumer (`ScormValidationConsumer`) picks up the event asynchronously and walks the record through `IN_PROGRESS` → `VALID`/`FAILED` → `COMPLETED`, updating Cassandra at each step.
3. A client can poll for the current status of a `resourceId` at any point.

**Not yet implemented:** the actual SCORM package validation (`imsmanifest.xml` parsing, tracking-call detection) and the FR-5 enhancement routine (re-zip/re-upload with an injected tracking wrapper) are placeholders — see the `TODO`s in `ScormValidationConsumer`.

## Prerequisites

- Java 17
- A running Cassandra cluster
- A running Kafka broker
- Network access to the iGOT Content Service (for reading `artifactUrl` by `resourceId`)
- Cloud storage credentials (Azure by default) for the SCORM package container

## Configuration

All configuration lives in `src/main/resources/application.properties`:

| Group | Properties | Notes |
|---|---|---|
| Cassandra | `cassandra.config.host`, `sunbird_cassandra_consistency_level` | Connection is opened by `common-util`'s `CassandraConnectionManagerImpl` (raw DataStax driver, not Spring Data Cassandra) |
| Kafka | `spring.kafka.bootstrap-servers`, `kafka.scorm.validation.request.topic(.group)` | Request/consumer topic for the async validation pipeline |
| Auth | `sso.url`, `sso.realm`, `accesstoken.publickey.basepath` | JWT verification, handled by `common-util`'s `AccessTokenValidator`/`KeyManager` |
| Cloud storage | `cloud.storage.*`, `cloud.container.name`, `scorm.validation.container.name` | Where uploaded SCORM packages live |
| Content Service | `content-service-host`, `content-read-endpoint` | Used to resolve a `resourceId` to its `artifactUrl` |

Both Cassandra and the auth beans are provided by `net.karmayogibharat:common-util`, auto-configured via its `CommonAutoConfiguration`.

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
    resourceid          text PRIMARY KEY,
    validationid        text,
    filename            text,
    artifacturl         text,
    enhancedartifacturl text,
    status              text,
    isenhanced          boolean,
    errorreason         text,
    requestedby         text,
    createdat           text,
    updatedat           text,
    retrycount          int
);
```

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

## Running locally

```sh
./mvnw spring-boot:run
```

The service starts on port `7001` (`server.port`).

## Status lifecycle

`STARTED` → `IN_PROGRESS` → `VALID` or `FAILED` → `COMPLETED` (`ENHANCED` is reserved for the not-yet-implemented FR-5 routine).

## Tests

```sh
./mvnw test
```
