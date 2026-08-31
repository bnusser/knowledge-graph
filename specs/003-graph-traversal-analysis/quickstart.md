# Quickstart: Graph Traversal and Analysis

## Prerequisites

- Java 21, Maven 3.9+, and Docker
- Feature 003 implemented in `java-core`
- `X-API-Key` value matching `APP_SECURITY_API_KEY`

The API contract is [contracts/traversal-api.yaml](./contracts/traversal-api.yaml), and result
invariants are documented in [data-model.md](./data-model.md).

## Start the complete demo stack with one command

```powershell
cd java-core
docker compose up --build
```

Expected: Compose builds and starts both Neo4j and the Java core, waits for Neo4j health before
starting the API, and exposes the API at `http://127.0.0.1:8080` using the local development key
`local-dev-key`. This is the constitution-compliant demo path.

## Foreground development alternative

```powershell
cd java-core
docker compose up -d neo4j
$env:APP_SECURITY_API_KEY = "local-dev-key"
mvn spring-boot:run
```

Use this alternative when the Java process should remain visible in the terminal for debugging.
Keep that terminal open and use a second PowerShell terminal for the requests below.

## Run automated validation

```powershell
cd java-core
mvn verify
```

Expected: unit tests and Testcontainers integration tests pass. The integration fixture covers a
line, branches, an equal-length alternative path, a cycle, a self-loop, parallel relationships,
mixed directions/types, an isolated entity, and a dense truncation frontier.

## Explore a neighborhood

Replace `<entity-id>` with an entity ID in the loaded graph.

```powershell
$headers = @{ "X-API-Key" = "local-dev-key" }
Invoke-RestMethod `
  -Headers $headers `
  -Uri "http://127.0.0.1:8080/entities/<entity-id>/neighborhood?maxHops=2&direction=both&limit=100"
```

Expected:

- HTTP 200.
- The start entity has `distance: 0`.
- Every entity has required `id`, `type`, and `properties` fields.
- Every relationship has required `id`, `type`, source/target endpoint IDs, and `properties`.
- Every relationship's endpoints are present in `entities`.
- `resultSize` equals the entity count plus relationship count and does not exceed 100.
- Entities are ordered by distance then ID; relationships by encounter hop then ID.

To filter to one or more schema-defined relationship types, repeat the query parameter:

```powershell
Invoke-RestMethod `
  -Headers $headers `
  -Uri "http://127.0.0.1:8080/entities/<entity-id>/neighborhood?relationshipTypes=TRANSACTION&relationshipTypes=FLOWS_TO&limit=1000"
```

A defined type with no instances is valid and produces no matching expansion. An undefined type
returns 422.

## Find a shortest path

```powershell
Invoke-RestMethod `
  -Headers $headers `
  -Uri "http://127.0.0.1:8080/entities/<source-id>/shortest-path/<destination-id>?maxHops=3&direction=outgoing&relationshipTypes=TRANSACTION"
```

Expected when connected: `outcome` is `found`, `hopCount` equals the number of relationships,
and entity/relationship arrays describe a complete source-to-destination route. When no allowed
route exists within the bound, HTTP remains 200 with `outcome: no_path`, `hopCount: null`, and
empty arrays. The same source and destination produce a zero-hop found result.

## Validate errors and authentication

```powershell
# Missing/invalid API key -> 401
Invoke-WebRequest -SkipHttpErrorCheck `
  -Uri "http://127.0.0.1:8080/entities/<entity-id>/neighborhood"

# Invalid semantic bound -> 422
Invoke-WebRequest -SkipHttpErrorCheck `
  -Headers $headers `
  -Uri "http://127.0.0.1:8080/entities/<entity-id>/neighborhood?maxHops=11"

# Missing endpoint -> 404
Invoke-WebRequest -SkipHttpErrorCheck `
  -Headers $headers `
  -Uri "http://127.0.0.1:8080/entities/not-present/neighborhood"
```

Each error uses the existing `{timestamp,status,error}` envelope.

## Full PaySim and Elliptic performance validation

This validation is opt-in and runs against an already loaded external stack; it must not start a
fresh Testcontainers graph. Load each dataset separately using feature 002's documented commands,
then run the external benchmark with an explicit dataset name, API URL, API key, and deterministic
seed manifest.

```powershell
cd java-core
$env:TRAVERSAL_PERF_NEO4J_URI = "bolt://localhost:7687"
$env:TRAVERSAL_PERF_NEO4J_USERNAME = "neo4j"
$env:TRAVERSAL_PERF_NEO4J_PASSWORD = "local-dev-password"
mvn verify `
  "-Dsurefire.excludedGroups=" `
  "-Dit.test=TraversalDatasetPerformanceIT" `
  "-Dtraversal.perf.enabled=true" `
  "-Dtraversal.perf.dataset=paysim" `
  "-Dtraversal.perf.apiUrl=http://127.0.0.1:8080" `
  "-Dtraversal.perf.apiKey=local-dev-key" `
  "-Dtraversal.perf.seedManifest=path\to\paysim-seeds.txt"
```

Repeat with `elliptic` and its seed manifest. The harness must:

- perform warmups, then at least 200 measured neighborhood requests per dataset;
- rotate deterministic low-, medium-, and high-degree seeds and workloads using hops 1..3,
  all directions, filtered/unfiltered requests, and limits through 1,000;
- verify successful, structurally valid responses;
- report p50, p95, p99, maximum, workload, dataset counts, and runtime configuration;
- pass only when at least 95% of measured requests finish below 2,000 ms; and
- confirm entity and relationship counts are unchanged before and after the run.

Record both reports and the resolved seed manifests in the feature validation evidence during
implementation. Do not place API keys in the manifests or committed output.
