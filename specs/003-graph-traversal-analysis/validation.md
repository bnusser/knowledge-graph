# Feature 003 Validation Evidence

## Automated semantic validation

Validated on 2026-08-31 with Java 21, Maven 3.9+, Docker Desktop, Neo4j 5.24 via
Testcontainers, and Spring Boot 3.3.4.

```powershell
cd java-core
mvn verify "-Dtest=TraversalServiceTest" "-Dit.test=TraversalIT"
```

Result: **PASS**. Nine unit tests and six HTTP/Testcontainers tests passed. Coverage includes
defaults and hard bounds, zero-hop behavior, cycles, self-loops, parallel and cross-branch edges,
endpoint closure, atomic truncation, stable breadth-first ordering, minimum-hop paths,
relationship-ID tie-breaking, `no_path`, all directions, repeated/normalized type filters,
400/401/404/422 behavior, generated OpenAPI paths, deterministic repeated requests, and unchanged
Neo4j node/relationship counts.

The Neo4j repository tests were also run independently:

```powershell
mvn verify "-Dtest=RelationshipTypeServiceTest" "-Dit.test=TraversalRepositoryIT"
```

Result: **PASS**. Four filter-validation tests and three parameterized Cypher integration tests
passed.

The complete default regression suite was then run:

```powershell
mvn verify
```

Result: **PASS** in 42.242 seconds. All unit tests and all 27 integration tests passed; opt-in
`@Tag("perf")` tests remained excluded as designed. A follow-up contract test confirmed generated
OpenAPI version 0.3.0, both traversal paths, and the strict required-field sets for both response
schemas.

## Full-data performance validation

The opt-in harness is `TraversalDatasetPerformanceIT`. It requires an already loaded dataset, a
running Java core, Neo4j connection environment variables, and a deterministic seed manifest. It
performs warmups and at least 200 measured HTTP requests, prints dataset counts and p50/p95/p99/max
latency, checks the 95% under-two-seconds threshold, validates response structure, and compares
graph counts before and after.

### PaySim

Status: **PASS** on 2026-09-03.

- Dataset counts before and after: 9,073,900 `Account` entities and 6,362,620 `TRANSACTION`
  relationships; counts were unchanged.
- Seed manifest: [performance/paysim-seeds.csv](./performance/paysim-seeds.csv), containing three
  deterministic seeds in each band: low degree 1, medium degree 2 (dataset p95), and high degree
  105–113.
- Workload: 24 warmups and 240 measured neighborhood requests, rotating hops 1–3, outgoing,
  incoming, and both directions, limits 100/500/1,000, and alternating filtered/unfiltered calls.
- Environment: Java 21.0.12.1, Windows 11, Spring Boot 3.3.4, Neo4j 5.24.2, loopback HTTP/Bolt.
- Latency: p50 **33 ms**, p95 **56 ms**, p99 **67 ms**, maximum **73 ms**.
- Under 2,000 ms: **100.00%** (required: at least 95%).

Command: `mvn verify "-Dsurefire.excludedGroups=" "-Dtest=TraversalLoggingTest"
"-Dit.test=TraversalDatasetPerformanceIT" "-Dtraversal.perf.enabled=true"
"-Dtraversal.perf.dataset=paysim" ...`. Result: **BUILD SUCCESS** in 38.423 seconds.

### Elliptic

Status: **PASS** on 2026-09-03.

- Dataset counts before and after: 203,769 `Transaction` entities and 234,355 `FLOWS_TO`
  relationships; counts were unchanged during the benchmark.
- Pre-validation reconciliation found one loader-missed source row, `96302549 → 46230282`, while
  both endpoints existed. It was restored once through `POST /relationships`; the resulting count
  matches all 234,355 data rows in `elliptic_txs_edgelist.csv`.
- Seed manifest: [performance/elliptic-seeds.csv](./performance/elliptic-seeds.csv), containing
  three deterministic seeds in each band: low degree 1, medium degree 2 (dataset median), and high
  degree 284–473.
- Workload: 24 warmups and 240 measured neighborhood requests, rotating hops 1–3, outgoing,
  incoming, and both directions, limits 100/500/1,000, and alternating filtered/unfiltered calls.
- Environment: Java 21.0.12.1, Windows 11, Spring Boot 3.3.4, Neo4j 5.24.2, loopback HTTP/Bolt.
- Latency: p50 **30 ms**, p95 **63 ms**, p99 **91 ms**, maximum **99 ms**.
- Under 2,000 ms: **100.00%** (required: at least 95%).

Command: `mvn verify "-Dsurefire.excludedGroups=" "-Dtest=TraversalLoggingTest"
"-Dit.test=TraversalDatasetPerformanceIT" "-Dtraversal.perf.enabled=true"
"-Dtraversal.perf.dataset=elliptic" ...`. Result: **BUILD SUCCESS** in 40.729 seconds.

The two datasets coexist as disconnected, type-distinct subgraphs in the preserved Neo4j volume.
The harness was executed separately for each dataset, scoped counts to that dataset's entity and
relationship types, used only its seed manifest and relationship filter, and verified its own
before/after counts. No destructive reset was required.

## Final verification

Completed on 2026-09-03 against both the Testcontainers fixture and the full local graph.

- `mvn verify`: **PASS**, all unit tests and all 27 integration tests, in 47.313 seconds.
- Live neighborhood quickstart: **PASS**, including start distance, endpoint closure,
  `resultSize`, ordering/limit behavior, type filtering, and explicit truncation (99 returned
  records with `limit=100`).
- Live shortest-path quickstart: **PASS** for a one-hop found route, a zero-hop same-entity route,
  and a cross-dataset `no_path` result.
- Live error/authentication quickstart: **PASS** for 401, 404, and 422 responses, including an
  undefined relationship type.
- Canonical/generated contract comparison: **PASS**. Both documents report API version 0.3.0,
  both traversal paths are present, and the generated neighborhood required-field set matches the
  canonical contract.
- Read-only check: **PASS**. Total graph counts were unchanged at 9,277,669 entities and 6,596,975
  relationships across the live quickstart requests.
