# cb-ext-scorm-validation
Scorm Validation

## Cassandra schema

No migration tooling is wired up (matching the rest of the platform's services) — run this once against the target cluster before first use:

```sh
CREATE KEYSPACE IF NOT EXISTS sunbird
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

CREATE TABLE sunbird.scorm_validation_status (
    resourceid text PRIMARY KEY,
    validationid text,
    filename text,
    artifacturl text,
    enhancedartifacturl text,
    status text,
    isenhanced boolean,
    errorreason text,
    requestedby text,
    createdat timestamp,
    updatedat timestamp,
    retrycount int
);
```
