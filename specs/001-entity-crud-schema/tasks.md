---

description: "Task list template for feature implementation"
---

# Tasks: Entity/Relationship CRUD + Schema Modeling

**Input**: Design documents from `/specs/001-entity-crud-schema/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, quickstart.md

**Tests**: Included. Constitution Principle III mandates unit + integration tests per stack
component for every feature, so test tasks are generated for each user story phase even though
the spec itself doesn't separately request them.

**Organization**: Tasks are grouped by user story (spec.md priorities P1/P2/P3) to enable
independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- File paths are relative to the repository root

## Path Conventions

Single Maven project `java-core/` at the repo root (per plan.md Project Structure):
`java-core/src/main/java/com/knowledgegraph/core/...`, `java-core/src/test/java/com/knowledgegraph/core/...`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create Maven project skeleton (standard layout) at `java-core/pom.xml` and `java-core/src/main/java/`, `java-core/src/test/java/`
- [ ] T002 [P] Add `spring-boot-starter-web` and `spring-boot-starter-data-neo4j` dependencies to `java-core/pom.xml`
- [ ] T003 [P] Add `springdoc-openapi-starter-webmvc-ui` dependency to `java-core/pom.xml`
- [ ] T004 [P] Add JUnit5, Mockito, and Testcontainers (Neo4j module) test dependencies to `java-core/pom.xml`
- [ ] T005 [P] Create `java-core/docker-compose.yml` with a Neo4j 5.x service (bolt 7687, http 7474, named volume)
- [ ] T006 [P] Create `java-core/src/main/resources/application.yml` with Neo4j connection settings and server bound to `localhost` only
- [ ] T007 [P] Create `java-core/src/main/resources/logback-spring.xml` for structured JSON logging
- [ ] T008 Create `KnowledgeGraphCoreApplication` main class in `java-core/src/main/java/com/knowledgegraph/core/KnowledgeGraphCoreApplication.java`

**Checkpoint**: `mvn spring-boot:run` starts an empty Spring Boot app connected to Neo4j.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain model, persistence, and cross-cutting scaffolding shared by every user story

**⚠️ CRITICAL**: No user story work can start until this phase is complete

- [ ] T009 [P] Create `PropertyDefinition` value class in `java-core/src/main/java/com/knowledgegraph/core/schema/PropertyDefinition.java`
- [ ] T010 [P] Create `EntityTypeDefinition` node entity in `java-core/src/main/java/com/knowledgegraph/core/schema/EntityTypeDefinition.java`
- [ ] T011 [P] Create `RelationshipTypeDefinition` node entity in `java-core/src/main/java/com/knowledgegraph/core/schema/RelationshipTypeDefinition.java`
- [ ] T012 [P] Create `Entity` node entity (id, type, properties map) in `java-core/src/main/java/com/knowledgegraph/core/entity/Entity.java`
- [ ] T013 [P] Create `Relationship` node entity (id, type, sourceEntityId, targetEntityId, properties map) in `java-core/src/main/java/com/knowledgegraph/core/relationship/Relationship.java`
- [ ] T014 [P] Create `EntityTypeDefinitionRepository` (`Neo4jRepository`) in `java-core/src/main/java/com/knowledgegraph/core/schema/EntityTypeDefinitionRepository.java`
- [ ] T015 [P] Create `RelationshipTypeDefinitionRepository` (`Neo4jRepository`) in `java-core/src/main/java/com/knowledgegraph/core/schema/RelationshipTypeDefinitionRepository.java`
- [ ] T016 [P] Create `EntityRepository` (`Neo4jRepository`, parameterized derived queries only) in `java-core/src/main/java/com/knowledgegraph/core/entity/EntityRepository.java`
- [ ] T017 [P] Create `RelationshipRepository` (`Neo4jRepository`, parameterized derived queries only) in `java-core/src/main/java/com/knowledgegraph/core/relationship/RelationshipRepository.java`
- [ ] T018 Create `SchemaValidator` interface with a permissive no-op default `@Bean` in `java-core/src/main/java/com/knowledgegraph/core/schema/SchemaValidator.java` (real enforcement lands in US3; US1/US2 call this from day one so no rework is needed later)
- [ ] T019 Create `EntityTypeService` and `EntityTypeController` with basic create/list, rejecting a create request whose name matches an existing `EntityTypeDefinition` with `409` (FR-017), in `java-core/src/main/java/com/knowledgegraph/core/schema/`
- [ ] T020 Create `RelationshipTypeService` and `RelationshipTypeController` with basic create/list, rejecting a create request whose name matches an existing `RelationshipTypeDefinition` with `409` (FR-017), in `java-core/src/main/java/com/knowledgegraph/core/schema/`
- [ ] T021 Create global exception handling (`@ControllerAdvice` mapping not-found/conflict/validation exceptions to 404/409/422) in `java-core/src/main/java/com/knowledgegraph/core/config/ApiExceptionHandler.java`
- [ ] T022 Configure springdoc OpenAPI metadata (title/version/description) and register the `ApiKeyAuth` security scheme (`X-API-Key` header) as a global requirement, matching `contracts/openapi.yaml`, in `java-core/src/main/java/com/knowledgegraph/core/config/OpenApiConfig.java`
- [ ] T023 [P] Write `ApiKeyAuthFilter` unit tests (valid key passes, missing/invalid key rejected) in `java-core/src/test/java/com/knowledgegraph/core/unit/ApiKeyAuthFilterTest.java`
- [ ] T024 Implement `ApiKeyAuthFilter`: check the `X-API-Key` header on every request against a value configured via `app.security.api-key` in `application.yml`/an environment variable, returning `401` with a JSON error body when missing or invalid; register it for all `/entities`, `/relationships`, `/entity-types`, and `/relationship-types` endpoints, in `java-core/src/main/java/com/knowledgegraph/core/config/ApiKeyAuthFilter.java` (satisfies Constitution Principle IV's minimum interim auth bar for this slice)

**Checkpoint**: Foundation ready — entity type and relationship type schemas can be registered and listed, and every request requires a valid API key; user story implementation can now begin in parallel.

## Phase 3: User Story 1 - Manage Graph Entities (Priority: P1) 🎯 MVP

**Goal**: Create, retrieve, update, and delete an entity of a registered type.

**Independent Test**: Register an entity type, create an entity of that type, retrieve it by ID, update a property, delete it — verify each step without needing relationships or schema enforcement.

- [ ] T025 [P] [US1] Write `EntityService` unit tests (Mockito) in `java-core/src/test/java/com/knowledgegraph/core/unit/EntityServiceTest.java`
- [ ] T026 [P] [US1] Write Entity CRUD Testcontainers integration test in `java-core/src/test/java/com/knowledgegraph/core/integration/EntityCrudIT.java`
- [ ] T027 [US1] Implement `EntityService` (create validates `type` exists via `EntityTypeDefinitionRepository`; get by id; list with optional type filter; update; delete) in `java-core/src/main/java/com/knowledgegraph/core/entity/EntityService.java`
- [ ] T028 [US1] Implement `EntityController` (`POST /entities`, `GET /entities`, `GET /entities/{id}`, `PATCH /entities/{id}`, `DELETE /entities/{id}`) in `java-core/src/main/java/com/knowledgegraph/core/entity/EntityController.java`

**Checkpoint**: User Story 1 independently functional and testable — this is the MVP.

## Phase 4: User Story 2 - Manage Relationships Between Entities (Priority: P2)

**Goal**: Create, retrieve, update, and delete a directed, typed relationship between two existing entities.

**Independent Test**: Create two entities, connect them with a relationship, retrieve it from either endpoint, update it, delete it, and confirm a relationship to a non-existent entity is rejected.

- [ ] T029 [P] [US2] Write `RelationshipService` unit tests (Mockito) in `java-core/src/test/java/com/knowledgegraph/core/unit/RelationshipServiceTest.java`
- [ ] T030 [P] [US2] Write Relationship CRUD + traversal Testcontainers integration test, including a self-relationship case, in `java-core/src/test/java/com/knowledgegraph/core/integration/RelationshipCrudIT.java`
- [ ] T031 [P] [US2] Write `EntityService` delete-with-relationships Testcontainers integration test: deletion is blocked when relationships exist, succeeds with `cascade=true`, and no relationship ever ends up referencing a deleted entity (FR-005, SC-004), in `java-core/src/test/java/com/knowledgegraph/core/integration/EntityDeleteWithRelationshipsIT.java`
- [ ] T032 [P] [US2] Write a large-scale relationship traversal performance test: seed ~1,000,000 entities / 5,000,000 relationships and assert single-hop retrieval from either endpoint entity completes in under 1 second (SC-003), tagged `@Tag("perf")` so it's excluded from the default fast test run, in `java-core/src/test/java/com/knowledgegraph/core/integration/RelationshipTraversalPerformanceIT.java`
- [ ] T033 [US2] Implement `RelationshipService` (create validates source/target entities and relationship type exist, allows source == target; retrieve by entity + direction; update; delete) in `java-core/src/main/java/com/knowledgegraph/core/relationship/RelationshipService.java`
- [ ] T034 [US2] Implement `RelationshipController` (`POST /relationships`, `PATCH /relationships/{id}`, `DELETE /relationships/{id}`, `GET /entities/{id}/relationships`) in `java-core/src/main/java/com/knowledgegraph/core/relationship/RelationshipController.java`
- [ ] T035 [US2] Extend `EntityService.delete` with relationship-blocking + `cascade=true` override (FR-005), using `RelationshipRepository`, in `java-core/src/main/java/com/knowledgegraph/core/entity/EntityService.java`

**Checkpoint**: User Stories 1 and 2 independently functional — full graph CRUD without schema enforcement.

## Phase 5: User Story 3 - Define and Enforce a Graph Schema (Priority: P3)

**Goal**: Entity/relationship types declare property rules and source/target restrictions, and all create/update requests are validated against them.

**Independent Test**: Define a schema for one entity type and one relationship type, then verify conforming requests succeed and non-conforming ones (wrong property type, missing required property, disallowed source/target type, duplicate identifying property) are rejected with an error naming the violated rule.

- [ ] T036 [P] [US3] Write `SchemaValidatorImpl` unit tests (required property, data type, source/target type, duplicate detection) in `java-core/src/test/java/com/knowledgegraph/core/unit/SchemaValidatorImplTest.java`
- [ ] T037 [P] [US3] Write schema enforcement Testcontainers integration test covering all three US3 acceptance scenarios in `java-core/src/test/java/com/knowledgegraph/core/integration/SchemaEnforcementIT.java`
- [ ] T038 [US3] Implement `SchemaValidatorImpl`: validate `identifyingProperty` references a declared property when an `EntityTypeDefinition` is created, in `java-core/src/main/java/com/knowledgegraph/core/schema/SchemaValidatorImpl.java`
- [ ] T039 [US3] Implement property validation (required-ness + data type) for entity and relationship create/update in `SchemaValidatorImpl`
- [ ] T040 [US3] Implement relationship type source/target type restriction validation in `SchemaValidatorImpl`
- [ ] T041 [US3] Implement duplicate-entity detection (type + identifying-property value) via a generated Neo4j uniqueness constraint plus a service-layer pre-check, in `java-core/src/main/java/com/knowledgegraph/core/entity/EntityService.java` and `java-core/src/main/java/com/knowledgegraph/core/schema/EntityTypeService.java`
- [ ] T042 [US3] Register `SchemaValidatorImpl` as the active `SchemaValidator` bean, replacing the Foundational no-op default, in `java-core/src/main/java/com/knowledgegraph/core/config/SchemaValidatorConfig.java`

**Checkpoint**: All three user stories functional — full CRUD with schema enforcement.

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T043 [P] Verify springdoc-generated OpenAPI output matches `specs/001-entity-crud-schema/contracts/openapi.yaml`; reconcile any drift
- [ ] T044 [P] Write a restart-persistence integration test: create entities, relationships, and schema definitions, restart the Spring application context against the same Neo4j container, and assert all data is still present and unchanged (FR-015, SC-005), in `java-core/src/test/java/com/knowledgegraph/core/integration/RestartPersistenceIT.java`
- [ ] T045 [P] Run all `quickstart.md` validation scenarios end-to-end against the `docker-compose` stack
- [ ] T046 [P] Add a `java-core/README.md` with run instructions, linking to `specs/001-entity-crud-schema/quickstart.md`
- [ ] T047 Audit every repository/query method for parameterized Cypher only, no string concatenation (Constitution Principle IV compliance pass)

## Dependencies & Execution Order

- **Phase 1 (Setup)** has no dependencies — start here.
- **Phase 2 (Foundational)** depends on Phase 1 and blocks all user stories. Includes the
  API-key auth filter (T023-T024), so every endpoint is authenticated from the first user story
  onward.
- **Phase 3 (US1)** depends only on Phase 2. This is the MVP — deliverable and demoable on its own.
- **Phase 4 (US2)** depends on Phase 2 and reuses `EntityService` from US1 (T035 extends it) — build after US1.
- **Phase 5 (US3)** depends on Phase 2 (`SchemaValidator` interface) and is easiest to validate end-to-end once US1/US2 exist, but `SchemaValidatorImpl`'s logic itself (T038-T040) only needs the Foundational domain model — it can be developed in parallel with US2 if desired.
- **Phase 6 (Polish)** depends on all prior phases being complete.

## Parallel Execution Examples

- Within Foundational: T009-T017 (distinct model/repository files) can run in parallel; T018-T022 depend on T009-T017 completing first; T023 can be written in parallel with T009-T017 (it doesn't depend on the domain model).
- Within US1: T025 and T026 (test files) can run in parallel with each other, before T027-T028.
- Within US2: T029, T030, T031, and T032 (test files) can run in parallel with each other, before T033-T035.
- Within US3: T036 and T037 can run in parallel with each other, before T038-T042.
- Within Polish: T043, T044, T045, and T046 can all run in parallel.

## Implementation Strategy

**MVP first**: Complete Phase 1 → Phase 2 → Phase 3 (US1) and stop there for a demoable
entity-CRUD slice. Add Phase 4 (US2) for relationships, then Phase 5 (US3) for schema
enforcement, each phase independently shippable and testable per the Independent Test
criteria above.

