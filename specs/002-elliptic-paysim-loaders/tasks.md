---

description: "Task list template for feature implementation"
---

# Tasks: Elliptic + PaySim Dataset Loaders

**Input**: Design documents from `/specs/002-elliptic-paysim-loaders/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/bulk-import.yaml, quickstart.md

**Tests**: Included. Constitution Principle III mandates unit + integration tests per stack
component for every feature, so test tasks are generated even though the spec itself doesn't
separately request them.

**Organization**: Tasks are grouped by user story (spec.md priorities P1/P2/P3) to enable
independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- File paths are relative to the repository root

## Path Conventions

Two projects touched: the existing `java-core/` (extended with bulk endpoints) and a new
`loaders/` Python package (per plan.md Project Structure).

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize the new `loaders/` Python project

- [X] T001 Create the `loaders/` Python project skeleton (`loaders/pyproject.toml`, `loaders/src/kg_loaders/`, `loaders/tests/`)
- [X] T002 [P] Add the `requests` runtime dependency to `loaders/pyproject.toml`
- [X] T003 [P] Add `pytest` and `responses` dev dependencies to `loaders/pyproject.toml`
- [X] T004 Create `loaders/src/kg_loaders/__init__.py`

**Checkpoint**: `uv sync` succeeds inside `loaders/`.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The bulk-import endpoints (java-core) and shared loader infrastructure (Python)
that both user stories depend on

**⚠️ CRITICAL**: No user story work can start until this phase is complete

- [X] T005 [P] Create `BulkItemResult` and `BulkResult` DTOs in `java-core/src/main/java/com/knowledgegraph/core/bulk/BulkItemResult.java` and `java-core/src/main/java/com/knowledgegraph/core/bulk/BulkResult.java`
- [X] T006 [P] Write `EntityService.createBulk` unit tests (a call with a mix of created/already-present/rejected items all succeeds overall) in `java-core/src/test/java/com/knowledgegraph/core/unit/EntityServiceTest.java`
- [X] T007 [P] Write `RelationshipService.createBulk` unit tests (same mixed-outcome coverage) in `java-core/src/test/java/com/knowledgegraph/core/unit/RelationshipServiceTest.java`
- [X] T008 [P] Write `BulkImportIT` Testcontainers integration test covering partial-batch success across both bulk endpoints (FR-012) in `java-core/src/test/java/com/knowledgegraph/core/integration/BulkImportIT.java`
- [X] T009 Implement `EntityService.createBulk(List<EntityCreateRequest>)`: validate and look up items independently, persist valid items in a parameterized batch, isolate write failures with per-item fallback, and map every outcome to a `BulkItemResult` (FR-012), in `java-core/src/main/java/com/knowledgegraph/core/entity/EntityService.java`
- [X] T010 Implement `RelationshipService.createBulk(List<RelationshipCreateRequest>)` with the same validation, batched persistence, outcome, and fallback-isolation semantics, in `java-core/src/main/java/com/knowledgegraph/core/relationship/RelationshipService.java`
- [X] T011 Add `POST /entities/bulk` endpoint (delegates to `EntityService.createBulk`) in `java-core/src/main/java/com/knowledgegraph/core/entity/EntityController.java`
- [X] T012 Add `POST /relationships/bulk` endpoint (delegates to `RelationshipService.createBulk`) in `java-core/src/main/java/com/knowledgegraph/core/relationship/RelationshipController.java`
- [X] T013 [P] Implement the shared `api_client.py` (authenticated HTTP client, configurable batch size, calls `/entities/bulk` and `/relationships/bulk`) in `loaders/src/kg_loaders/api_client.py`
- [X] T014 [P] Write unit tests for `api_client.py` (batching boundaries, `X-API-Key` header, HTTP error handling, a connection failure stops the run and reports the count processed so far per FR-010, and N records with batch size B produce exactly `ceil(N/B)` HTTP calls per SC-007) using `responses`, in `loaders/tests/test_api_client.py`
- [X] T015 [P] Implement `summary.py` (`LoadSummary` tracking: created/already_present/skipped/skip_reasons/elapsed_seconds, plus printing) in `loaders/src/kg_loaders/summary.py`

**Checkpoint**: Bulk endpoints are callable and return per-item results; the Python loaders
have a working shared HTTP client and summary tracker. User story implementation can begin.

## Phase 3: User Story 1 - Load the Elliptic Bitcoin Dataset (Priority: P1) 🎯 MVP

**Goal**: Import Elliptic's transactions and flows into the graph via the bulk endpoints.

**Independent Test**: Point the loader at local Elliptic dataset files and confirm the
resulting graph's transaction count, flow-relationship count, and class labels match the
source files, without needing the PaySim loader to exist.

- [X] T016 [P] [US1] Write unit tests for `elliptic_loader.py` (schema registration, streaming parse of nodes/edges/classes, a row referencing an unknown transaction id is skipped with a reason, batching via the shared client) in `loaders/tests/test_elliptic_loader.py`
- [X] T017 [US1] Implement `elliptic_loader.py`: register the `Transaction`/`FLOWS_TO` schema if not already present, stream the nodes/edges/classes files, submit records in batches via `api_client`, print a summary via `summary.py`, in `loaders/src/kg_loaders/elliptic_loader.py`

**Checkpoint**: User Story 1 independently functional and testable — this is the MVP.

## Phase 4: User Story 2 - Load the PaySim Dataset (Priority: P2)

**Goal**: Import PaySim's accounts and transactions (with amounts and fraud flags) into the
graph via the bulk endpoints, with an optional row limit.

**Independent Test**: Point the loader at a local PaySim CSV and confirm the resulting
graph's account count, transaction-relationship count, and amount/type/fraud properties are
correct, independent of whether the Elliptic loader has been run.

- [X] T018 [P] [US2] Write unit tests for `paysim_loader.py` (schema registration, streaming parse, `--limit` takes a deterministic first-N-rows slice, string-to-boolean coercion for `isFraud`/`isFlaggedFraud`, a malformed row is skipped with a reason) in `loaders/tests/test_paysim_loader.py`
- [X] T019 [US2] Implement `paysim_loader.py`: register the `Account`/`TRANSACTION` schema if not already present, stream the CSV (honoring `--limit`), submit records in batches via `api_client`, print a summary via `summary.py`, in `loaders/src/kg_loaders/paysim_loader.py`
- [X] T020 [P] [US2] Write a large-scale PaySim load test, marked opt-in/slow (e.g. `@pytest.mark.slow`, excluded from the default fast run), that loads the full ~6.3M-row file (or a synthetic file of equivalent size) and asserts it completes without exhausting memory and using a bulk-request count consistent with the configured batch size (SC-006, SC-007), in `loaders/tests/test_paysim_scale.py`

**Checkpoint**: User Stories 1 and 2 both independently functional.

## Phase 5: User Story 3 - Review a Load Summary (Priority: P3)

**Goal**: Every load run ends with a clear, accurate summary (created/already-present/skipped
counts, skip reasons, elapsed time).

**Independent Test**: Run either loader against a small fixture file with a known mix of
valid and invalid rows and confirm the printed summary's counts match that fixture exactly.

- [X] T021 [P] [US3] Write an end-to-end summary-accuracy test using a small fixture file with known valid/invalid rows, run against both loaders, plus a re-run of the same fixture asserting the second run's `already_present` count matches the first run's `created` count (SC-004), in `loaders/tests/test_load_summary_accuracy.py`
- [X] T022 [US3] Enhance `summary.py`'s output: human-readable skip-reason listing and elapsed-time formatting, in `loaders/src/kg_loaders/summary.py`

**Checkpoint**: All three user stories functional — both loaders work end-to-end with clear
reporting.

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T023 [P] Merge the new bulk endpoints into `specs/001-entity-crud-schema/contracts/openapi.yaml` so the one canonical `java-core` API contract stays complete and in sync
- [X] T024 [P] Add `loaders/README.md` with install/run instructions, linking to `specs/002-elliptic-paysim-loaders/quickstart.md`
- [X] T025 [P] Update the root `README.md`'s architecture/status section to mention the dataset loaders
- [X] T026 Run all `quickstart.md` validation scenarios end-to-end against the `docker-compose` stack (partial-batch failure, idempotent re-run, row-limit determinism, full-scale PaySim run)
- [X] T027 Audit the new bulk endpoints' repository/service calls for parameterized Cypher only, no string concatenation (Constitution Principle IV compliance pass)

## Dependencies & Execution Order

- **Phase 1 (Setup)** has no dependencies — start here.
- **Phase 2 (Foundational)** depends on Phase 1 and blocks all user stories — it delivers the
  bulk endpoints (java-core) and shared `api_client.py`/`summary.py` (loaders) both stories
  need.
- **Phase 3 (US1)** depends only on Phase 2. This is the MVP.
- **Phase 4 (US2)** depends only on Phase 2 — fully independent of US1, can be built in
  parallel with it.
- **Phase 5 (US3)** depends on Phase 2 (`summary.py` exists); T022 (enhance summary
  formatting) only touches Foundational code and can proceed in parallel with US1/US2, but
  T021's end-to-end fixture and re-run test is most naturally run once at least one loader
  (US1 or US2) exists.
- **Phase 6 (Polish)** depends on all prior phases being complete.

## Parallel Execution Examples

- Within Foundational: T005 (Java DTOs), T006-T008 (Java tests), and T013/T014/T015 (Python
  shared modules) can all run in parallel with each other (different files/languages); T009-T012
  (Java implementation) depend on T005-T008 completing first.
- US1 and US2 (Phases 3 and 4) can be developed entirely in parallel — different files, no
  shared dependencies beyond Phase 2.
- Within US1: T016 before T017. Within US2: T018 before T019, and T020 (large-scale test)
  can be written in parallel with T018.
- Within Polish: T023, T024, and T025 can all run in parallel.

## Implementation Strategy

**MVP first**: Complete Phase 1 → Phase 2 → Phase 3 (US1) and stop there for a working
Elliptic loader. Add Phase 4 (US2) for PaySim — independently, in parallel if desired — then
Phase 5 (US3) for polished summary reporting, each phase independently shippable per the
Independent Test criteria above.

## Phase 7: Convergence

- [X] T028 CRITICAL Add loader integration and large-scale PaySim coverage in `loaders/tests/test_load_summary_accuracy.py` and `loaders/tests/test_paysim_scale.py`, including bounded-memory and bulk-request-count assertions, per Constitution III, T020, T021, SC-006, and SC-007
- [X] T029 Refactor `loaders/src/kg_loaders/paysim_loader.py`, `loaders/src/kg_loaders/api_client.py`, and summary result consumption so a full PaySim run does not retain all account names, account results, account IDs, or relationship results in memory per FR-004 and SC-006
- [X] T030 Optimize `java-core` entity and relationship bulk persistence to avoid per-record Neo4j lookup round-trips while preserving validation and outcome reporting, and add performance-focused integration coverage, per SC-007 and the plan performance goal
- [X] T031 Make bulk entity duplicate handling and write-failure isolation preserve `already_present`/created/rejected outcomes for every item, including duplicate identifiers within one request, in `EntityService`, `EntityRepository`, and bulk tests per FR-007, FR-012, and the research duplicate-account decision
- [X] T032 Run and document the full real PaySim validation against the local stack, verifying 9,073,900 distinct Account entities, 6,362,620 TRANSACTION relationships, required properties, summary totals, memory behavior, and bulk-request count per US2/AC1, SC-002, and T026
- [X] T033 Validate every loader input file for existence, readability, and expected structure before registering schemas or making other API changes, with regression tests for both loaders, per the missing/unreadable-file edge case
- [X] T034 Merge the feature-002 bulk endpoint paths and schemas into `specs/001-entity-crud-schema/contracts/openapi.yaml` and verify them against the implemented DTOs per T023 and the plan contract decision
- [X] T035 Reconcile the concurrent `--max-workers` implementation and tests with the synchronous-client research decision by documenting the accepted concurrency design, bounded in-flight behavior, ordering, and failure-count semantics per the research HTTP-client decision
- [X] T036 Reconcile the `dataset` discriminator added to Account/Transaction and Elliptic schemas, payloads, and tests with `spec.md`, `data-model.md`, and API contracts, documenting it if retained or removing it if unjustified, per the plan schema mappings
- [X] T037 Correct the PaySim scale assumptions and expected counts in the feature plan, quickstart, and validation documentation to reflect the actual 6,362,620 rows and 9,073,900 distinct accounts per the plan scale/scope decision
- [X] T038 Add `loaders/README.md` with installation, Elliptic and PaySim commands, scale expectations, troubleshooting, and a link to the feature quickstart per T024
