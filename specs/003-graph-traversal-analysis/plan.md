# Implementation Plan: Graph Traversal and Analysis

**Branch**: `003-graph-traversal-analysis` | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-graph-traversal-analysis/spec.md`

## Summary

Add API-key-protected, read-only neighborhood and shortest-path operations to the Java core.
A dedicated traversal repository will issue parameterized, batched one-hop Neo4j queries for
each breadth-first frontier. A Java service will enforce cycle safety, deterministic ordering,
combined neighborhood limits, schema-defined relationship filters, and lexicographic shortest-
path tie-breaking. The design adds no production dependency and keeps all graph behavior in the
Java source-of-truth layer.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3.4, Spring Data Neo4j/`Neo4jClient`, Neo4j 5.24,
springdoc-openapi 2.6.0; no APOC, GDS, or other new production dependency

**Storage**: Existing Neo4j graph: `:Entity` nodes and fixed native `:RELATES` relationships;
semantic entity/relationship types remain `type` properties backed by schema-definition nodes

**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot integration tests, Testcontainers Neo4j;
opt-in `@Tag("perf")` external full-dataset benchmark

**Target Platform**: Existing localhost-bound Java/Spring Boot web service on Windows or Linux,
with Neo4j available through Bolt. The demo stack runs with one `docker compose up --build`
command; foreground Maven startup remains available for development.

**Project Type**: Additive REST API and domain-service slice within the existing `java-core`
web service

**Performance Goals**: At least 95% of warmed API requests using at most three hops and a
neighborhood limit no greater than 1,000 complete in under two seconds, measured separately on
fully loaded PaySim and Elliptic graphs

**Constraints**: Hop bounds are 0..10 (default 3); neighborhood combined record limit is
1..1,000 (default 100); the first candidate expansion that cannot fit ends the traversal;
shortest paths have no separate result limit and are complete or absent; all graph input is
validated and all Cypher values are bound parameters; operations are synchronous and read-only

**Scale/Scope**: Full PaySim graph (9,073,900 Account entities and 6,362,620 TRANSACTION
relationships) and full Elliptic graph (~203,769 Transaction entities and ~234,355 FLOWS_TO
relationships), plus representative cyclic, branching, parallel-edge, self-loop, disconnected,
and dense-hub fixtures

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

- **I. Layered Polyglot Architecture, Java as Source of Truth** — PASS. Traversal validation,
  persistence queries, BFS state, limits, ordering, and path selection all live in `java-core`.
  Other layers receive only a documented REST contract.
- **II. Contract-First Integration** — PASS. `contracts/traversal-api.yaml` defines both
  endpoints, strict required response fields, errors, and authentication before implementation.
  Implementation will merge this additive contract into the canonical feature 001 OpenAPI
  document and verify generated springdoc output.
- **III. Test Discipline Per Stack** — PASS. The plan includes Java unit tests, Testcontainers
  integration tests, contract assertions, and an explicit full-dataset performance harness.
- **IV. Secure-by-Default Graph Access (NON-NEGOTIABLE)** — PASS. Both endpoints remain under
  `/entities`, so the existing API-key filter protects them. Direction selects one of three
  constant queries; IDs, type filters, frontiers, seen IDs, and limits are bound parameters.
  No caller value is interpolated into Cypher.
- **V. Interview-Grade Code Quality & Explainability** — PASS. The dedicated traversal package,
  explicit BFS invariants, deterministic ordering rules, and research rationale keep the design
  idiomatic and explainable without a framework-specific graph algorithm black box.
- **VI. North-Star Scope with MVP-First Delivery** — PASS. Bounded neighborhood traversal is
  the P1 MVP. Shortest unweighted path and filters follow; weighted algorithms, centrality,
  similarity, saved queries, streaming, and distributed execution remain out of scope.
- **VII. Demo-Readiness & Observability** — PASS. A multi-stage `java-core/Dockerfile` and the
  existing Compose definition provide a one-command `docker compose up --build` demo stack for
  Neo4j plus the Java core. Foreground Maven startup remains a documented development option.
  Each valid operation emits one structured completion event with bounds, sanitized filter
  context, result counts, outcome, truncation state, and elapsed time.

**Post-Phase 1 re-check**: `research.md`, `data-model.md`, `contracts/traversal-api.yaml`, and
`quickstart.md` were reviewed against all seven principles. The design adds no cross-layer graph
logic, no unprotected route, no unsafe query construction, and no unexplained dependency. Gate
remains PASS.

## Project Structure

### Documentation (this feature)

```text
specs/003-graph-traversal-analysis/
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- contracts/
|   `-- traversal-api.yaml
`-- tasks.md                     # generated later by /speckit-tasks
```

### Source Code (repository root)

```text
java-core/
|-- Dockerfile                                  # multi-stage Java 21 application image
|-- docker-compose.yml                          # Neo4j + java-core one-command demo stack
|-- src/main/java/com/knowledgegraph/core/
|   |-- traversal/
|   |   |-- TraversalController.java
|   |   |-- TraversalService.java
|   |   |-- TraversalRepository.java
|   |   |-- TraversalDirection.java
|   |   |-- TraversalEdge.java
|   |   |-- NeighborhoodResultDto.java
|   |   |-- TraversedEntityDto.java
|   |   |-- TraversedRelationshipDto.java
|   |   `-- ShortestPathResultDto.java
|   |-- exception/
|   |   `-- InvalidTraversalRequestException.java
|   |-- schema/
|   |   `-- RelationshipTypeService.java        # validate defined filter types
|   `-- config/
|       `-- ApiExceptionHandler.java             # uniform 400/422 bodies
`-- src/test/java/com/knowledgegraph/core/
    |-- unit/
    |   `-- TraversalServiceTest.java
    |-- integration/
    |   `-- TraversalIT.java
    `-- performance/
        `-- TraversalDatasetPerformanceIT.java   # opt-in external loaded graph

specs/001-entity-crud-schema/contracts/
`-- openapi.yaml                                 # merge additive traversal contract
```

**Structure Decision**: Add a cohesive `traversal` package rather than extending CRUD
repositories and services. The traversal repository owns read projections and three fixed
directional query variants; the traversal service owns graph semantics and deterministic BFS;
the controller owns only HTTP mapping. Existing entity, relationship, schema, authentication,
error, and logging infrastructure is reused.

## Complexity Tracking

No constitution violations require justification.
