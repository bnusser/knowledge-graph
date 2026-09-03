# java-core

Java/Spring Boot core of the Knowledge Graph project: entity/relationship CRUD, schema modeling,
bounded neighborhood traversal, and deterministic shortest paths, backed by Neo4j. See the
[core CRUD](../specs/001-entity-crud-schema/) and
[graph traversal](../specs/003-graph-traversal-analysis/) feature artifacts.

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Docker (for Neo4j and Testcontainers)

## Start the complete demo stack

```powershell
docker compose up --build
```

This builds the Java 21 application image, waits for Neo4j to become healthy, and exposes the API
at `http://127.0.0.1:8080`. The default local API key is `local-dev-key`.

## Foreground development alternative

```powershell
docker compose up -d neo4j
$env:APP_SECURITY_API_KEY = "local-dev-key"   # must match application.yml's app.security.api-key
mvn spring-boot:run
```

The API listens on `http://127.0.0.1:8080`. Every request to `/entities`, `/relationships`,
`/entity-types`, and `/relationship-types` requires an `X-API-Key: local-dev-key` header.

- Swagger UI: `http://127.0.0.1:8080/swagger-ui.html`
- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`

## Graph traversal API

All traversal requests require `X-API-Key`. Defaults are `maxHops=3`, `direction=both`, and—for
neighborhoods—`limit=100`. Supported hop bounds are 0..10; neighborhood limits are 1..1,000.

```powershell
$headers = @{ "X-API-Key" = "local-dev-key" }

# Bounded neighborhood; relationshipTypes may be repeated.
Invoke-RestMethod -Headers $headers `
  -Uri "http://127.0.0.1:8080/entities/<id>/neighborhood?maxHops=2&direction=both&relationshipTypes=TRANSACTION&limit=100"

# Complete shortest eligible path or outcome=no_path; there is no separate path result limit.
Invoke-RestMethod -Headers $headers `
  -Uri "http://127.0.0.1:8080/entities/<source>/shortest-path/<destination>?maxHops=3&direction=outgoing"
```

Directions are `outgoing`, `incoming`, or `both` (case-insensitive). Relationship filters are
validated against schema-defined types. Invalid syntax returns 400, missing endpoints return 404,
and invalid bounds/directions/types return 422. See the
[feature quickstart](../specs/003-graph-traversal-analysis/quickstart.md) for response invariants
and complete examples.

## Test

```powershell
mvn verify                                     # unit + Testcontainers integration tests; excludes @Tag("perf")

# Legacy synthetic 1M-entity/5M-relationship regression (slow and opt-in)
mvn verify "-Dsurefire.excludedGroups=" "-Dtest=*Test" "-Dit.test=RelationshipTraversalPerformanceIT"
```

The full-data traversal benchmark is a separate opt-in HTTP test against an already loaded
external PaySim or Elliptic stack. Its seed manifest uses non-comment lines in the form
`degree-band,entity-id[,relationship-type]`:

```powershell
$env:TRAVERSAL_PERF_NEO4J_URI = "bolt://localhost:7687"
$env:TRAVERSAL_PERF_NEO4J_USERNAME = "neo4j"
$env:TRAVERSAL_PERF_NEO4J_PASSWORD = "local-dev-password"
mvn verify `
  "-Dsurefire.excludedGroups=" `
  "-Dtest=*Test" `
  "-Dit.test=TraversalDatasetPerformanceIT" `
  "-Dtraversal.perf.enabled=true" `
  "-Dtraversal.perf.dataset=paysim" `
  "-Dtraversal.perf.apiUrl=http://127.0.0.1:8080" `
  "-Dtraversal.perf.apiKey=local-dev-key" `
  "-Dtraversal.perf.seedManifest=path\to\paysim-seeds.txt"
```

Repeat with `elliptic` and its seed manifest. The harness performs warmups and at least 200
measured requests, reports p50/p95/p99/max, requires at least 95% under two seconds, validates
response structure, and checks Neo4j counts before and after.

## Validation walkthrough

See the [CRUD quickstart](../specs/001-entity-crud-schema/quickstart.md) and
[traversal quickstart](../specs/003-graph-traversal-analysis/quickstart.md) for scenario-by-scenario
walkthroughs matching their acceptance criteria.
