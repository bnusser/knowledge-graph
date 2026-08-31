# Phase 0 Research: Graph Traversal and Analysis

## Decision: Use batched one-hop queries plus Java breadth-first search

**Decision**: Add a dedicated `TraversalRepository` using `Neo4jClient` and a
`TraversalService` that performs level-by-level BFS. The repository reads a complete frontier
in one round trip rather than querying once per entity.

**Rationale**: The graph stores every edge as native `:RELATES` with semantic type in the
parameterizable `r.type` property. Application BFS can implement the exact combined-limit,
cycle, ordering, and shortest-path tie-break rules without enumerating every variable-length
path. One query per frontier avoids an N+1 query pattern.

**Alternatives considered**:

- Cypher `shortestPath`/`allShortestPaths`: rejected because a single shortest path does not
  guarantee the required equal-path winner, while enumerating all equal paths can explode.
- APOC or Neo4j GDS: rejected because neither is a portable declared Java-core dependency and
  the feature does not need an additional algorithm runtime.
- One adjacency query per entity: rejected because dense/wide frontiers would create excessive
  Bolt round trips.

## Decision: Select direction with constant query templates

**Decision**: Use exactly three constant Cypher templates for `OUTGOING`, `INCOMING`, and
`BOTH`. Bind entity IDs, frontier IDs, semantic types, already-seen relationship IDs, and query
limits as parameters. Never interpolate a caller-supplied direction, type, ID, or bound.

**Rationale**: Cypher structural directions are syntax, not bindable values. A validated enum
selecting constant source code preserves parameterization and matches the existing relationship
repository pattern. Semantic types remain safely filtered with `r.type IN $relationshipTypes`.

**Alternatives considered**: Constructing a relationship pattern from request text was rejected
as unnecessary query injection risk. Treating semantic values as native Neo4j relationship types
was rejected because it contradicts the authoritative persistence model.

## Decision: Bound neighborhood reads before materializing dense frontiers

**Decision**: A neighborhood repository query receives a candidate cap equal to remaining
combined result capacity plus one, excludes relationship IDs already returned, deduplicates an
edge reachable from multiple frontier entities, sorts by relationship ID, and applies the cap.
The extra candidate is enough to detect truncation when every candidate costs one record.

**Rationale**: Fetching every relationship adjacent to a dense hub would undermine the API's
1,000-record safety bound. A candidate always consumes at least one new relationship record, so
no valid result prefix can require more than `remaining + 1` unseen candidates.

**Alternatives considered**: Fetching the entire frontier and truncating only in Java was
rejected for avoidable database, network, and heap use. A raw `LIMIT 1000` was rejected because
the start record and caller limit change the remaining budget.

## Decision: Deterministic neighborhood prefix semantics

**Decision**: Keep insertion maps for entities, relationship IDs, minimum entity distances, and
relationship encounter hops. Expand one depth at a time. Within a depth, process distinct
relationships by ID. A candidate costs one for a new relationship plus one for each endpoint not
already returned. If its full cost exceeds remaining capacity, omit it, set `truncated=true`, and
stop without inspecting later candidates. Return entities sorted by `(distance, id)` and
relationships by `(encounterHop, id)`.

**Rationale**: This exactly implements the clarified deterministic-prefix rule, endpoint closure,
and combined count. ID maps make cycles, self-loops, parallel edges, duplicate matches, and
cross-branch connections safe while retaining all eligible edges encountered within the bound.

**Alternatives considered**: Skipping an oversized candidate to fill later gaps was rejected
because it breaks prefix semantics. Returning only discovery-tree edges was rejected because it
would hide cycles, parallel relationships, and local graph structure.

## Decision: Deterministic shortest path via lexicographically ordered BFS

**Decision**: For shortest path, keep a path state per frontier entity containing its ordered
relationship-ID sequence and parent link. For each depth, form candidate paths and sort by the
full relationship-ID sequence, using the next entity ID only as a final total-order guard. The
first discovery of each entity is retained; the first destination discovery is returned.

**Rationale**: Breadth-first depth guarantees minimum hops. Processing complete candidate
sequences lexicographically guarantees the smallest relationship-ID sequence among equal-hop
routes. Parent links reconstruct ordered entities and relationships without copying full graph
objects into every queue item.

**Alternatives considered**: A database-selected arbitrary shortest path was rejected because it
cannot satisfy the specified tie break. Keeping every path to every node was rejected as
unnecessary and unsafe; the first lexicographically ordered discovery dominates later equal-depth
paths to that node.

## Decision: Zero-hop and validation semantics

**Decision**: Accept `maxHops=0`. A zero-hop neighborhood returns only the start entity and is
not truncated. A zero-hop path succeeds only when source equals destination; otherwise it is a
valid no-path result. Negative hops, hops above 10, neighborhood limits below 1 or above 1,000,
unknown directions, and schema-undefined relationship types return 422 before graph expansion.

**Rationale**: Zero hops is a useful mathematically consistent bound and aligns with the required
same-endpoint path. A neighborhood limit must accommodate the mandatory starting entity.

**Alternatives considered**: Rejecting zero was rejected because it would remove the explicitly
required zero-hop path behavior. Treating unknown types as 404 was rejected because the spec
classifies them as invalid filter input, not a missing traversal endpoint.

## Decision: Schema definitions determine filter validity

**Decision**: Extend `RelationshipTypeService` with a read-only bulk validation operation. Empty
filters normalize to all types; otherwise duplicate names are removed, names are sorted for
stable echo/logging, and every name must exist as a `RelationshipTypeDefinition`. A defined type
with no instances remains valid.

**Rationale**: Schema validity must not change merely because current data is empty. Bulk
validation also avoids repeated schema lookups and keeps schema knowledge out of the controller
and repository.

**Alternatives considered**: Checking distinct `r.type` values in graph data was rejected as
unstable and potentially expensive. Accepting arbitrary strings was rejected because it would
hide caller errors.

## Decision: Entity-centric additive REST contract

**Decision**: Add two GET operations:

- `/entities/{entityId}/neighborhood`
- `/entities/{sourceEntityId}/shortest-path/{destinationEntityId}`

Use query parameters for bounds, direction, and repeated relationship types. No-path is HTTP 200
with an explicit `no_path` outcome; missing endpoints are 404; malformed query syntax is 400;
semantic validation errors are 422. Both endpoints use the existing `X-API-Key` scheme.

**Rationale**: Existing read operations are entity-centric GET endpoints. Keeping the routes
under `/entities` automatically applies the current authentication prefix and avoids a new auth
surface. Explicit result bodies distinguish a valid empty/no-path answer from errors.

**Alternatives considered**: POST request bodies were rejected because the bounded query fields
fit naturally in a GET. A new `/traversals` prefix was rejected because it would require auth
filter expansion and depart from current route conventions.

## Decision: Layered automated tests and opt-in full-data validation

**Decision**: Use Mockito unit tests for BFS invariants and validation, ordinary Testcontainers
integration tests for real Cypher/API/auth/error behavior, and an opt-in external `@Tag("perf")`
benchmark for fully loaded PaySim and Elliptic graphs. The real-data harness must not extend the
shared Testcontainers base. It warms the service, rotates a reproducible manifest of low/medium/
high-degree seeds, runs at least 200 neighborhood requests per dataset, and reports p50/p95/p99,
maximum, workload, counts, and environment. It verifies at least 95% complete below 2,000 ms and
confirms node/relationship counts are unchanged.

**Rationale**: Small fixtures provide fast deterministic semantic coverage. Loading PaySim for
every CI run is impractical and does not belong in the shared test container; an explicit
external profile validates the actual loaded graph and end-to-end HTTP latency.

**Alternatives considered**: A synthetic-only benchmark was rejected as insufficient for SC-005.
JMH was rejected because it would not include HTTP, Spring, Bolt, and Neo4j behavior.

## Decision: One sanitized completion log per valid operation

**Decision**: Emit one INFO completion event containing operation, endpoint IDs, normalized
bounds, direction, relationship-type count, result counts, truncation, outcome, and elapsed
milliseconds. Do not log API keys, graph properties, or request headers. Test representative log
events and ensure dynamic values cannot break the existing JSON console pattern.

**Rationale**: A single bounded event satisfies live-demo diagnostics without logging graph
payloads or adding a metrics dependency.

**Alternatives considered**: Per-hop INFO logging was rejected as noisy and potentially costly;
adding Micrometer was deferred because the current success criteria require diagnostic context,
not a new monitoring subsystem.

## Decision: Provide one-command demo startup and a separate foreground development path

**Decision**: Add a multi-stage Java 21 `java-core/Dockerfile` and extend
`java-core/docker-compose.yml` with a Java-core service that waits for healthy Neo4j, uses
`bolt://neo4j:7687`, overrides the in-container server address to `0.0.0.0`, publishes port 8080
only on host loopback, and defaults to the non-empty local development API key. The complete demo
stack starts with `docker compose up --build`. Keep the existing Neo4j-only Compose plus foreground
`mvn spring-boot:run` flow as a documented development alternative.

**Rationale**: This satisfies Constitution Principle VII without removing the foreground workflow
used during active development and debugging. The same API-key filter and loopback-oriented local
configuration remain authoritative inside either startup mode.

**Alternatives considered**: A PowerShell-only wrapper was rejected because it would not be a
portable full-stack command. Requiring separate Compose and Maven commands was rejected because it
does not satisfy the constitution's explicit one-command demo requirement.
