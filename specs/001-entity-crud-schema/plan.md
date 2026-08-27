# Implementation Plan: Entity/Relationship CRUD + Schema Modeling

**Branch**: `001-entity-crud-schema` | **Date**: 2026-08-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-entity-crud-schema/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Build the Java/Spring Boot core of the knowledge graph: CRUD for typed entities and typed,
directed relationships, plus schema definitions (entity types, relationship types, property
rules) that all create/update requests are validated against, backed by Neo4j. This is the
foundational, source-of-truth slice (Constitution Principle I) that every later layer (MCP
server, LangGraph agent, React UI) will depend on via its OpenAPI contract.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.x (`spring-boot-starter-web`,
`spring-boot-starter-data-neo4j`), Spring Data Neo4j (SDN), springdoc-openapi
(`springdoc-openapi-starter-webmvc-ui`) for OpenAPI 3 contract generation, Maven as build tool.

**Storage**: Neo4j 5.x (single local instance for this slice, run via Docker)

**Testing**: JUnit5 + Mockito for unit tests; Testcontainers (Neo4j module) for integration
tests against a real, ephemeral Neo4j instance

**Target Platform**: Linux container image for eventual deployment; runs locally via Docker
Compose during development, protected by API-key auth on every request (see Constitution Check)

**Project Type**: Web service (single backend project for this slice, within a multi-service
repo that will later add `mcp-server/`, `agent/`, `frontend/`)

**Performance Goals**: Single-hop relationship lookup (from either endpoint entity) in under 1
second at target scale (SC-003)

**Constraints**: All graph queries MUST be parameterized (no string-concatenated Cypher) per
Constitution Principle IV; every endpoint requires a valid API key (`X-API-Key` header),
satisfying Principle IV's minimum interim auth bar for this slice — OAuth2/JWT remains the
target end-state and is deferred to a dedicated security slice

**Scale/Scope**: Up to 1,000,000 entities / 5,000,000 relationships (spec SC-003)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Layered Polyglot Architecture, Java as Source of Truth** — PASS. This feature only
  builds the Java core; no business logic is introduced in another language.
- **II. Contract-First Integration** — PASS (enforced by Phase 1). The REST API is specified
  as an OpenAPI document (`contracts/openapi.yaml`) before controller implementation.
- **III. Test Discipline Per Stack** — PASS (enforced by task planning). JUnit5 + Mockito unit
  tests and Testcontainers-backed integration tests are required for this Java slice.
- **IV. Secure-by-Default Graph Access (NON-NEGOTIABLE)** — PASS. All Cypher access goes
  through Spring Data Neo4j's parameterized query mechanism (derived queries / `@Query` with
  named parameters), never string concatenation, satisfying the injection-prevention half of
  the principle. Every endpoint requires a valid `X-API-Key` header (`ApiKeyAuthFilter`,
  Foundational phase), satisfying the NON-NEGOTIABLE "never ship a network-exposed endpoint
  with no authentication" half at its stated minimum interim bar. OAuth2/JWT remains the
  target end-state and is deferred to a dedicated security slice per Principle VI.
- **V. Interview-Grade Code Quality & Explainability** — PASS (enforced by task planning).
  Standard Spring conventions, layered packages, Javadoc/comments only where non-obvious.
- **VI. North-Star Scope with MVP-First Delivery** — PASS. This slice is exactly the minimal
  demoable cut (CRUD + schema) called for by the constitution; traversal beyond single-hop,
  graph algorithms, the query DSL, and NLP extraction are explicitly out of scope here.
- **VII. Demo-Readiness & Observability** — PASS (enforced by task planning). Docker Compose
  will bring up Neo4j + this service with one command; Spring Boot structured logging
  (JSON via logback) is required.

**Post-Phase 1 re-check**: `research.md`, `data-model.md`, `contracts/openapi.yaml`, and
`quickstart.md` were reviewed against all seven principles above after design — no new
violations introduced (contracts define the API, including the `ApiKeyAuth` security scheme,
before implementation per II; the data model carries no auth/session fields since auth is a
header-level filter concern, not a domain-model concern, under IV; quickstart runs the
whole stack with two commands per VII). Gate remains PASS.

## Project Structure

### Documentation (this feature)

```text
specs/001-entity-crud-schema/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── openapi.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
java-core/                             # This feature's home (Spring Boot / Maven project)
├── pom.xml
├── docker-compose.yml                 # Neo4j + java-core, single-command local run
├── src/
│   ├── main/
│   │   ├── java/com/knowledgegraph/core/
│   │   │   ├── entity/                # Entity domain model, repository, service, controller
│   │   │   ├── relationship/          # Relationship domain model, repository, service, controller
│   │   │   ├── schema/                # EntityTypeDefinition, RelationshipTypeDefinition, PropertyDefinition
│   │   │   └── config/                # OpenAPI config, API-key auth filter, exception handling, schema validator wiring
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/knowledgegraph/core/
│           ├── unit/                  # JUnit5 + Mockito
│           └── integration/           # Testcontainers-backed Neo4j tests
└── target/                            # build output (gitignored)

# Future slices (not created by this feature):
# mcp-server/  agent/  frontend/
```

**Structure Decision**: Single Maven project `java-core/` at the repo root, using standard
Maven layout. This repo will become a multi-service monorepo as later feature slices add
`mcp-server/`, `agent/`, and `frontend/` alongside it — no shared code between them at this
stage, consistent with Principle I (Java core is the only place with business logic).

## Complexity Tracking

*No unjustified Constitution Check violations — Principle IV is satisfied directly (API-key
auth implemented in Foundational, T023-T024) rather than via a scoped exception, so no
Complexity Tracking entries are required.*

