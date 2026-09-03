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

Status: **not yet executed for this implementation run**. Dataset counts, resolved seeds,
workload, environment, percentile report, and read-only comparison must be appended here after a
fully loaded PaySim stack and deterministic manifest are available.

### Elliptic

Status: **not yet executed for this implementation run**. Dataset counts, resolved seeds,
workload, environment, percentile report, and read-only comparison must be appended here after a
fully loaded Elliptic stack and deterministic manifest are available.

Read-only inspection of the available `kg-neo4j` container found 9,277,669 `Entity` nodes and
6,596,974 `RELATES` relationships, including both 9,073,900 `Account` entities and 203,769
`Transaction` entities. This is a co-loaded PaySim + Elliptic graph, while T031/T032 require each
dataset to be benchmarked separately. The API was also not running. No destructive dataset reset
was performed, so T031, T032, and the final all-scenarios task T036 remain open.
