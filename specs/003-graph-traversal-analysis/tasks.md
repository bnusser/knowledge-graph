# Tasks: Graph Traversal and Analysis

**Input**: Design documents from `/specs/003-graph-traversal-analysis/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`,
`contracts/traversal-api.yaml`, `quickstart.md`

**Tests**: Required by FR-016 and Constitution Principle III. Within each story, write the
listed tests first and confirm they fail for the expected missing behavior before implementation.

**Organization**: Tasks are grouped by user story so each increment has an explicit independent
test. Task IDs are ordered for the recommended single-developer implementation sequence.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel with adjacent tasks because it changes different files and does
  not depend on incomplete behavior.
- **[Story]**: Maps the task to User Story 1, 2, or 3.
- Every task names the exact repository-relative file or files it changes or validates.

## Phase 1: Setup (Shared Contract)

**Purpose**: Establish the additive public contract before implementation.

- [ ] T001 Merge both feature 003 paths, parameters, strict required response schemas, and API version 0.3.0 from `specs/003-graph-traversal-analysis/contracts/traversal-api.yaml` into `specs/001-entity-crud-schema/contracts/openapi.yaml`, rewrite merged schema references as local `#/components/...` references, and update metadata in `java-core/src/main/java/com/knowledgegraph/core/config/OpenApiConfig.java`
- [ ] T002 [P] Add a multi-stage Java 21 application image and a health-ordered, API-key-protected Java-core Compose service using `bolt://neo4j:7687`, in-container `SERVER_ADDRESS=0.0.0.0`, and host-loopback port publishing so `docker compose up --build` starts the complete demo stack in `java-core/Dockerfile` and `java-core/docker-compose.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build shared validation, query safety, and traversal projections used by every user
story.

**CRITICAL**: Complete this phase before implementing any user story.

- [ ] T003 [P] Add failing unit tests for empty, duplicate, defined-unused, and undefined relationship-type filter validation in `java-core/src/test/java/com/knowledgegraph/core/unit/RelationshipTypeServiceTest.java`
- [ ] T004 [P] Add `InvalidTraversalRequestException` and uniform 400/422 error mappings in `java-core/src/main/java/com/knowledgegraph/core/exception/InvalidTraversalRequestException.java` and `java-core/src/main/java/com/knowledgegraph/core/config/ApiExceptionHandler.java`
- [ ] T005 Implement bulk schema-defined type validation and stable filter normalization required by T003 in `java-core/src/main/java/com/knowledgegraph/core/schema/RelationshipTypeService.java`
- [ ] T006 [P] Implement case-insensitive request parsing and canonical `outgoing`, `incoming`, and `both` values in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalDirection.java`
- [ ] T007 [P] Implement the internal current/next/source/target/relationship read projection in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalEdge.java`
- [ ] T008 Add failing Neo4j integration tests for parameterized batched frontier reads, native direction, semantic type filtering, deduplication, and candidate caps in `java-core/src/test/java/com/knowledgegraph/core/integration/TraversalRepositoryIT.java`
- [ ] T009 Implement fixed-direction parameterized neighborhood and path frontier queries required by T008 in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalRepository.java`

**Checkpoint**: Shared schema validation and safe one-hop expansion are ready.

---

## Phase 3: User Story 1 - Explore a Bounded Neighborhood (Priority: P1) MVP

**Goal**: Return a deterministic, cycle-safe neighborhood within the requested hop and combined
record bounds.

**Independent Test**: Seed branching, cyclic, self-loop, parallel-edge, cross-branch, isolated,
and disconnected structures; request a two-hop neighborhood and verify unique endpoint-closed
records, minimum distances, stable ordering, atomic truncation, and no graph mutations.

### Tests for User Story 1

- [ ] T010 [P] [US1] Add failing unit tests for defaults, zero-hop results, breadth-first distance/order, cycles, self-loops, parallel/cross-branch edges, endpoint closure, and first-nonfitting-candidate truncation in `java-core/src/test/java/com/knowledgegraph/core/unit/TraversalServiceTest.java`
- [ ] T011 [P] [US1] Add failing HTTP/Testcontainers tests for bounded, isolated, repeated deterministic, missing-start, and read-only neighborhood requests in `java-core/src/test/java/com/knowledgegraph/core/integration/TraversalIT.java`

### Implementation for User Story 1

- [ ] T012 [P] [US1] Implement entity response fields plus minimum hop distance in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversedEntityDto.java`
- [ ] T013 [P] [US1] Implement relationship response fields plus encounter hop in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversedRelationshipDto.java`
- [ ] T014 [US1] Implement normalized bounds, result size, truncation, and ordered record collections in `java-core/src/main/java/com/knowledgegraph/core/traversal/NeighborhoodResultDto.java`
- [ ] T015 [US1] Implement bounded level-order neighborhood BFS, visited maps, atomic candidate costing, candidate-cap calculation, and final deterministic sorting in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalService.java`
- [ ] T016 [US1] Implement `GET /entities/{entityId}/neighborhood` with defaults and DTO mapping in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalController.java`
- [ ] T017 [US1] Emit one sanitized neighborhood completion event with applied bounds, record counts, truncation, outcome, and elapsed time in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalService.java`

**Checkpoint**: User Story 1 is a runnable and independently testable neighborhood MVP.

---

## Phase 4: User Story 2 - Find a Shortest Path (Priority: P2)

**Goal**: Return one complete minimum-hop route with deterministic equal-path selection, or an
explicit no-path outcome.

**Independent Test**: Seed routes of different and equal lengths and verify minimum hops,
lexicographically smallest relationship-ID sequence, ordered reconstruction, zero-hop same-
endpoint behavior, bound exclusion, and explicit no-path results.

### Tests for User Story 2

- [ ] T018 [P] [US2] Add failing unit tests for minimum-hop discovery, lexicographic equal-path tie-breaking, cycle-safe parent reconstruction, zero-hop paths, hop bounds, and no-path outcomes in `java-core/src/test/java/com/knowledgegraph/core/unit/TraversalServiceTest.java`
- [ ] T019 [P] [US2] Add failing HTTP/Testcontainers tests for found, equal-route, same-endpoint, disconnected, missing-endpoint, and bound-excluded shortest paths in `java-core/src/test/java/com/knowledgegraph/core/integration/TraversalIT.java`

### Implementation for User Story 2

- [ ] T020 [P] [US2] Implement `found`/`no_path`, nullable hop count, and ordered entity/relationship response collections in `java-core/src/main/java/com/knowledgegraph/core/traversal/ShortestPathResultDto.java`
- [ ] T021 [US2] Implement lexicographically ordered level BFS, first-discovery parent state, destination selection, and complete path reconstruction in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalService.java`
- [ ] T022 [US2] Implement `GET /entities/{sourceEntityId}/shortest-path/{destinationEntityId}` without a separate result limit in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalController.java`
- [ ] T023 [US2] Emit one sanitized shortest-path completion event with applied bounds, route counts, `found`/`no_path` outcome, and elapsed time in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalService.java`

**Checkpoint**: User Stories 1 and 2 both pass their independent acceptance tests.

---

## Phase 5: User Story 3 - Constrain and Predict Traversal Results (Priority: P3)

**Goal**: Apply configurable direction and schema-defined type filters consistently, reject
invalid input predictably, enforce hard limits, and validate behavior on full loaded datasets.

**Independent Test**: Repeat neighborhood and path requests over mixed directions/types and a
dense frontier; verify filter consistency, hard-bound errors, deterministic truncation, API-key
protection, uniform errors, sanitized logs, and the two-second criterion on PaySim and Elliptic.

### Tests for User Story 3

- [ ] T024 [P] [US3] Add failing unit tests for hop/limit hard bounds, direction parsing, empty/duplicate/defined-unused/undefined type filters, normalized filter echo, and consistent filters across both operations in `java-core/src/test/java/com/knowledgegraph/core/unit/TraversalServiceTest.java`
- [ ] T025 [P] [US3] Add failing HTTP/Testcontainers tests for incoming/outgoing/both, repeated type parameters, dense truncation, malformed versus semantic errors, API-key rejection, generated OpenAPI paths, and unchanged graph counts in `java-core/src/test/java/com/knowledgegraph/core/integration/TraversalIT.java`
- [ ] T026 [P] [US3] Add failing captured-output tests for one parseable completion event per operation and omission of API keys, credentials, and unrelated graph properties in `java-core/src/test/java/com/knowledgegraph/core/unit/TraversalLoggingTest.java`

### Implementation for User Story 3

- [ ] T027 [US3] Enforce 0..10 hops, 1..1,000 neighborhood limits, canonical direction, schema-defined types, and identical filter behavior for neighborhoods and paths in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalService.java`
- [ ] T028 [US3] Bind repeated `relationshipTypes`, `direction`, `maxHops`, and neighborhood `limit` query parameters to both public operations in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalController.java`
- [ ] T029 [US3] Complete sanitized structured completion logging required by T026 without logging properties or secrets in `java-core/src/main/java/com/knowledgegraph/core/traversal/TraversalService.java`
- [ ] T030 [US3] Implement an opt-in external `@Tag("perf")` HTTP benchmark with warmups, deterministic seed manifests, at least 200 measured requests, latency percentiles, structural assertions, and before/after Neo4j counts in `java-core/src/test/java/com/knowledgegraph/core/performance/TraversalDatasetPerformanceIT.java`
- [ ] T031 [US3] Run T030 against the fully loaded PaySim graph and record dataset counts, resolved seeds, workload, environment, p50/p95/p99/max, under-two-second percentage, and read-only count comparison in `specs/003-graph-traversal-analysis/validation.md`
- [ ] T032 [US3] Run T030 separately against the fully loaded Elliptic graph and append dataset counts, resolved seeds, workload, environment, p50/p95/p99/max, under-two-second percentage, and read-only count comparison in `specs/003-graph-traversal-analysis/validation.md`

**Checkpoint**: All three user stories and full-scale acceptance criteria are validated.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Align documentation, existing performance guidance, and final verification evidence.

- [ ] T033 [P] Document the one-command Compose demo stack, foreground Maven development alternative, both traversal endpoints, default verification, and opt-in benchmark commands in `java-core/README.md`
- [ ] T034 [P] Add feature 003 usage and design-artifact links to the graph traversal section in `README.md`
- [ ] T035 Correct the legacy performance-test Maven guidance while preserving its synthetic regression role in `java-core/src/test/java/com/knowledgegraph/core/integration/RelationshipTraversalPerformanceIT.java` and `java-core/README.md`
- [ ] T036 Run `mvn verify`, execute every applicable scenario in `specs/003-graph-traversal-analysis/quickstart.md`, verify the canonical OpenAPI document against `/v3/api-docs`, and record commands and outcomes in `specs/003-graph-traversal-analysis/validation.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 — Setup**: Starts immediately; T001 and T002 can run in parallel because they modify
  contract/configuration and container-startup files respectively.
- **Phase 2 — Foundational**: Depends on Phase 1 and blocks all user stories. T003/T004/T006/T007
  can start together; T005 follows T003; T008 is written before T009.
- **Phase 3 — User Story 1**: Depends on Phase 2 and produces the MVP.
- **Phase 4 — User Story 2**: Depends on Phase 2 conceptually, but follows US1 in the recommended
  sequence because both stories extend `TraversalService` and `TraversalController`.
- **Phase 5 — User Story 3**: Depends on US1 and US2 so filters and validation can be proven
  consistent across both operations. T031 and T032 require T030 and separately loaded datasets.
- **Phase 6 — Polish**: Depends on every story selected for delivery; T036 follows T031/T032 for
  a complete feature validation record.

### User Story Dependency Graph

```text
Setup -> Foundation -> US1 (MVP) -> US2 -> US3 -> Polish
```

- **US1** is the first demoable slice and does not require shortest path.
- **US2** reuses only shared traversal infrastructure and remains independently testable through
  its shortest-path endpoint.
- **US3** intentionally integrates with both earlier operations because its acceptance criterion
  is consistent filters and limits across them.

### Within Each User Story

- Write the story's tests and confirm expected failures before implementation.
- Create response records before service methods that construct them.
- Complete service behavior before exposing or extending controller mappings.
- Run the story's unit and integration tests at its checkpoint.

## Parallel Opportunities

- Setup tasks T001 and T002 modify different contract/configuration and container-startup files.
- Foundational tests/error/enum/projection work in T003, T004, T006, and T007 is independent.
- US1 unit and integration tests T010/T011 can be written together; DTOs T012/T013 can be built
  together after their expected shapes are fixed.
- US2 unit and integration tests T018/T019 can be written together; T020 is independent of them.
- US3 unit, integration, and logging tests T024/T025/T026 target different files.
- Documentation T033/T034 can proceed together after the public behavior is stable.

## Parallel Example: User Story 1

```text
Task T010: Write neighborhood service tests in TraversalServiceTest.java
Task T011: Write neighborhood HTTP/Neo4j tests in TraversalIT.java

Task T012: Implement TraversedEntityDto.java
Task T013: Implement TraversedRelationshipDto.java
```

## Parallel Example: User Story 2

```text
Task T018: Extend TraversalServiceTest.java with shortest-path tests
Task T019: Extend TraversalIT.java with shortest-path API tests
Task T020: Implement ShortestPathResultDto.java
```

## Parallel Example: User Story 3

```text
Task T024: Extend TraversalServiceTest.java with filter/bound tests
Task T025: Extend TraversalIT.java with API/security/contract tests
Task T026: Create TraversalLoggingTest.java
```

## Implementation Strategy

### MVP First: User Story 1

1. Complete Setup and Foundational phases.
2. Write T010 and T011 and confirm expected failures.
3. Complete T012–T017.
4. Run the US1 test subset and manually exercise the neighborhood endpoint.
5. Stop here for the smallest demoable traversal release if necessary.

### Incremental Delivery

1. **Foundation**: Safe parameterized frontier reads and schema-aware validation.
2. **US1**: Bounded neighborhood exploration — independently test and demo.
3. **US2**: Deterministic shortest path — independently test and demo.
4. **US3**: Direction/type controls, hardening, observability, and real-data performance proof.
5. **Polish**: Documentation, canonical contract verification, and final evidence.

## Notes

- `[P]` means different files and no dependency on unfinished behavior; tasks sharing
  `TraversalService.java`, `TraversalController.java`, or `TraversalIT.java` remain sequential.
- All Cypher request values must be bound parameters; only the validated direction enum may
  select among constant query templates.
- The one-command demo path is `docker compose up --build`; foreground Maven remains the
  development/debugging alternative.
- Default verification is `mvn verify`; full PaySim/Elliptic runs are explicit opt-in work.
- Do not commit API keys, Neo4j passwords, or populated benchmark seed files containing secrets.
- Commit after each task or coherent task group, and preserve unrelated working-tree changes.
