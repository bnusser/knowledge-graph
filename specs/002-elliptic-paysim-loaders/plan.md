# Implementation Plan: Elliptic + PaySim Dataset Loaders

**Branch**: `002-elliptic-paysim-loaders` | **Date**: 2026-08-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-elliptic-paysim-loaders/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Build two Python CLI loaders (Elliptic, PaySim) that stream a local dataset file and populate
the knowledge graph via the Java core's API, plus a new bulk-import capability added to that
same API (`POST /entities/bulk`, `POST /relationships/bulk`) so large loads don't require one
HTTP round-trip per record. The loaders contain no graph business logic — they parse files,
batch records, and call the existing (extended) API, consistent with Constitution Principle I.

## Technical Context

**Language/Version**: Python 3.12+ for the loaders; Java 21 for the bulk-import addition to
the existing `java-core` service (feature 001)

**Primary Dependencies**: Loaders: `requests` (HTTP), `uv`/`pyproject.toml` for dependency
management, `pytest` + `responses` for tests. Bulk endpoints: no new dependencies — extend
`java-core`'s existing Spring Boot/Spring Data Neo4j stack.

**Storage**: Neo4j 5.x — the same instance and schema model as feature 001; no new storage
technology.

**Testing**: `pytest` (loaders, with the target API mocked via `responses`) + JUnit5/Mockito
and an extended Testcontainers integration test (bulk endpoints, `java-core`)

**Target Platform**: Loaders run as local CLI scripts on a developer machine; the bulk
endpoints run inside the existing `java-core` service (same localhost-bound, API-key-protected
deployment as feature 001)

**Project Type**: CLI tooling (new `loaders/` project) + REST API extension (existing
`java-core`)

**Performance Goals**: A full PaySim load (~6.3M rows) completes using on the order of
thousands of bulk HTTP requests (default batch size 500) rather than millions of individual
requests (SC-007)

**Constraints**: Bulk endpoints MUST remain fully parameterized Cypher (Principle IV); a
single invalid record in a batch MUST NOT prevent the rest of the batch from succeeding
(FR-012); loaders MUST stream their source file rather than loading it fully into memory
(FR-004); loaders authenticate with the same `X-API-Key` header feature 001 already requires

**Scale/Scope**: Elliptic (~203K transaction entities / ~234K flow relationships); PaySim (up
to ~6.3M transaction relationships, with a few hundred thousand distinct account entities
derived from the `nameOrig`/`nameDest` columns)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Layered Polyglot Architecture, Java as Source of Truth** — PASS. The new bulk-import
  logic (per-item validation looping, partial-success semantics) lives entirely in
  `java-core`'s existing `EntityService`/`RelationshipService`. The Python loaders contain no
  graph business logic — they only parse files, batch records, and call the API.
- **II. Contract-First Integration** — PASS (enforced by Phase 1). The two new endpoints are
  specified in `contracts/bulk-import.yaml` and merged into feature 001's
  `contracts/openapi.yaml` before implementation.
- **III. Test Discipline Per Stack** — PASS. `pytest` for the Python loaders (feature 001's
  Python/pytest standard applies here too, ahead of the future agent slice); JUnit5/Mockito +
  an extended Testcontainers IT for the Java-side bulk endpoints.
- **IV. Secure-by-Default Graph Access (NON-NEGOTIABLE)** — PASS. `/entities/bulk` and
  `/relationships/bulk` both start with `/entities`/`/relationships`, so they're automatically
  covered by the existing `ApiKeyAuthFilter` path-prefix check — no filter changes needed. All
  bulk Cypher access reuses the existing parameterized repository methods per item; no new
  string-built Cypher is introduced.
- **V. Interview-Grade Code Quality & Explainability** — PASS. Loaders follow PEP 8/ruff;
  Java additions follow the existing Spring conventions from feature 001.
- **VI. North-Star Scope with MVP-First Delivery** — PASS. Explicitly bounded per the spec's
  Assumptions (developer/demo tooling, not a production ingestion pipeline; no scheduling,
  incremental updates, or rollback).
- **VII. Demo-Readiness & Observability** — PASS. Loaders print a Load Summary (FR-008);
  `java-core`'s existing structured logging covers the new endpoints without changes.

**Post-Phase 1 re-check**: `research.md`, `data-model.md`, `contracts/bulk-import.yaml`, and
`quickstart.md` were reviewed against all seven principles after design — no new violations
introduced (the bulk contract is defined before implementation per II; it reuses feature
001's existing schemas by reference rather than duplicating them; the loaders remain pure
API callers with zero validation/business logic of their own per I). Gate remains PASS.

## Project Structure

### Documentation (this feature)

```text
specs/002-elliptic-paysim-loaders/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── bulk-import.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
loaders/                                  # New: Python CLI loaders
├── pyproject.toml
├── src/kg_loaders/
│   ├── __init__.py
│   ├── api_client.py                     # Shared HTTP client: auth header, batching, retries
│   ├── summary.py                        # Load Summary tracking + printing
│   ├── elliptic_loader.py                # Reads nodes.csv/edges.csv/classes.csv
│   └── paysim_loader.py                  # Reads the PaySim CSV
└── tests/
    ├── test_api_client.py
    ├── test_elliptic_loader.py
    └── test_paysim_loader.py

java-core/                                # Existing project (feature 001), extended
├── src/main/java/com/knowledgegraph/core/
│   ├── entity/EntityService.java         # + createBulk(List<EntityCreateRequest>)
│   ├── entity/EntityController.java      # + POST /entities/bulk
│   ├── relationship/RelationshipService.java       # + createBulk(...)
│   └── relationship/RelationshipController.java    # + POST /relationships/bulk
└── src/test/java/com/knowledgegraph/core/
    ├── unit/EntityServiceTest.java                 # + bulk test cases
    ├── unit/RelationshipServiceTest.java            # + bulk test cases
    └── integration/BulkImportIT.java                # new Testcontainers IT
```

**Structure Decision**: New top-level `loaders/` Python project (its own `pyproject.toml`,
independent of `java-core` and any future `agent/`), since these are developer/demo tooling
rather than one of the four architectural layers. The bulk-import capability itself is added
directly to `java-core`'s existing `EntityService`/`RelationshipService`/controllers rather
than new classes, since it's the same validation logic looped over multiple items, not a
distinct concern.

## Complexity Tracking

*No unjustified Constitution Check violations — the bulk-import logic stays in `java-core`
per Principle I, so no Complexity Tracking entries are required.*

