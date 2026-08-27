# Feature Specification: Elliptic + PaySim Dataset Loaders

**Feature Branch**: `002-elliptic-paysim-loaders`

**Created**: 2026-08-27

**Status**: Draft

**Input**: User description: "design both an Elliptic and PaySim loader"

## Clarifications

### Session 2026-08-27

- Q: Should this feature only use the existing single-record REST endpoints, even if that makes a full 6.3-million-row PaySim load very slow, or should it also add a bulk/batch ingestion capability? → A: Add a new bulk-import endpoint to the Java core API as part of this feature, and have both loaders use it instead of one-record-per-request calls.
- Q: When a developer limits a load to N rows, should the loader take the first N rows of the file, or a random sample spread across the file? → A: First N rows — simple and deterministic.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Load the Elliptic Bitcoin Dataset (Priority: P1)

As a developer preparing a demo or test run, I can import the Elliptic Bitcoin dataset
(transactions and their flows) into the running system, so that I have a realistic,
already-labeled illicit/licit transaction network available to exercise traversal and
graph-algorithm capabilities against.

**Why this priority**: Elliptic is the smaller, already graph-shaped dataset (nodes + edges),
making it the fastest path to a working, demonstrable load and the natural first target.

**Independent Test**: Can be fully tested by pointing the loader at a local copy of the
Elliptic `nodes.csv`/`edges.csv`/`classes.csv` files and confirming the resulting graph
contains the expected transaction count, flow-relationship count, and class labels, without
needing the PaySim loader to exist.

**Acceptance Scenarios**:

1. **Given** local Elliptic dataset files and a running system with no prior Elliptic data
   loaded, **When** a developer runs the Elliptic loader, **Then** every transaction becomes
   an entity and every flow becomes a relationship, and a summary reports how many of each
   were loaded.
2. **Given** an Elliptic dataset row referencing a transaction id not present in the node
   file, **When** the loader processes that row, **Then** the affected row is reported as
   skipped with a reason, and the overall load continues.
3. **Given** a dataset already partially loaded, **When** the loader is run again on the same
   files, **Then** previously-loaded transactions are not duplicated (reported as
   already-present rather than re-created).

---

### User Story 2 - Load the PaySim Dataset (Priority: P2)

As a developer preparing a demo or test run, I can import the PaySim synthetic mobile-money
dataset (accounts and their transactions, including amounts and fraud flags) into the running
system, so that I have amount-based fraud analytics and large-scale data available to test
and demonstrate.

**Why this priority**: PaySim is the larger, amount-bearing dataset; it builds on the same
loading approach validated by the Elliptic loader (User Story 1) but adds real financial
properties and much greater volume.

**Independent Test**: Can be fully tested by pointing the loader at a local copy of the PaySim
CSV and confirming the resulting graph contains the expected account count, transaction-
relationship count, and that amount/type/fraud properties are present, independent of whether
the Elliptic loader has been run.

**Acceptance Scenarios**:

1. **Given** a local PaySim CSV file and a running system, **When** a developer runs the
   PaySim loader, **Then** every distinct account name becomes an entity, every row becomes a
   transaction relationship carrying amount/type/fraud-flag properties, and a summary reports
   how many of each were loaded.
2. **Given** a developer who does not want to load all ~6.3 million PaySim rows,
   **When** they run the loader with a row-count limit, **Then** only that many rows are
   processed and the summary reflects the limited count.
3. **Given** a PaySim row that fails schema validation (e.g., an unexpected value), **When**
   the loader processes that row, **Then** the row is reported as skipped with a reason, and
   the overall load continues.

---

### User Story 3 - Review a Load Summary (Priority: P3)

As a developer, I can see a clear summary after any load run (how many entities/relationships
were created, skipped, or failed, and how long it took), so that I can judge whether the data
is ready to demo without manually inspecting the graph.

**Why this priority**: Builds on User Stories 1 and 2; useful but not required for a first
working load — a developer can otherwise inspect the graph directly via the existing API.

**Independent Test**: Can be fully tested by running either loader against a small sample file
with a mix of valid and invalid rows and confirming the printed summary's counts match the
known content of that sample file.

**Acceptance Scenarios**:

1. **Given** a completed load run with some skipped rows, **When** the run finishes, **Then**
   the summary states counts of created, already-present, and skipped records, plus elapsed
   time.

---

### Edge Cases

- What happens when the source dataset file is missing or unreadable? The loader reports a
  clear error identifying the missing file and stops before making any changes.
- What happens when the target system's required entity/relationship types haven't been
  defined yet? The loader defines them itself before loading data (see Assumptions).
- How does the loader handle a source file far larger than available memory (e.g., full
  PaySim or a full IMDb-scale file)? Rows are streamed and processed in batches rather than
  loaded entirely into memory at once.
- What happens if the target system becomes unreachable partway through a load (e.g., API
  restarts)? The loader reports how many records were successfully loaded before the failure
  and stops, so the developer knows exactly how far the run got.
- What happens when a bulk-import batch contains a mix of valid and invalid records? The
  valid records in that batch are still created; only the invalid ones are rejected and
  reported individually — one bad record does not discard the rest of the batch.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a loader that reads a local Elliptic dataset (node,
  edge, and class files) and creates a corresponding transaction entity and flow relationship
  for each valid row.
- **FR-002**: System MUST provide a separate loader that reads a local PaySim dataset file
  and creates a corresponding account entity and transaction relationship (with amount, type,
  and fraud-flag properties) for each valid row.
- **FR-003**: Each loader MUST define its required entity type(s) and relationship type(s) in
  the target system before loading data, if they are not already defined.
- **FR-004**: Each loader MUST process its source file in a streamed/batched manner rather
  than reading the entire file into memory at once.
- **FR-005**: Each loader MUST support limiting a run to the first N rows of the source file
  (a deterministic prefix, not a random sample), to allow smaller test loads without
  processing an entire large dataset.
- **FR-006**: Each loader MUST continue processing after encountering an invalid or
  rejected row, recording it as skipped with a reason rather than aborting the whole run.
- **FR-007**: Each loader MUST treat an entity that already exists (per the target system's
  duplicate-detection rules) as already-present rather than as an error, and continue.
- **FR-008**: Each loader MUST produce a summary at the end of a run reporting counts of
  records created, already-present, and skipped, plus total elapsed time.
- **FR-009**: Each loader MUST authenticate to the target system using the same API-key
  mechanism the system already requires.
- **FR-010**: If the target system becomes unreachable during a run, the loader MUST stop and
  report how many records were processed before the failure.
- **FR-011**: System MUST provide one or more bulk-import endpoints that accept multiple
  entities and/or relationships in a single request; both loaders MUST use them instead of
  issuing one request per record.
- **FR-012**: The bulk-import endpoint MUST process every valid record in a batch even when
  other records in the same batch are invalid, and MUST report the outcome (created/
  already-present/rejected-with-reason) for each record individually.

### Key Entities

- **Elliptic Transaction Record**: One row of the Elliptic dataset — a Bitcoin transaction
  with a unique id, its time step, and a licit/illicit/unknown class label; becomes a graph
  entity. (The dataset's ~165 anonymized numeric features are not carried into the graph —
  see Assumptions.)
- **Elliptic Flow Record**: One edge of the Elliptic dataset — a directed flow from one
  transaction to another; becomes a graph relationship.
- **PaySim Transaction Row**: One row of the PaySim dataset — a transfer between two named
  accounts with a type, amount, before/after balances, and a fraud flag; becomes a graph
  relationship (with the two accounts becoming entities on first appearance).
- **Load Summary**: The result of one loader run — counts of created, already-present, and
  skipped records, plus elapsed time and a list of skip reasons.
- **Bulk Import Batch**: A group of entity and/or relationship records submitted together in
  one request; each record within the batch succeeds or is rejected independently of the
  others.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer can go from "have the Elliptic dataset files on disk" to "graph
  populated with all transactions and flows" in a single command, with a printed summary at
  the end.
- **SC-002**: A developer can go from "have the PaySim dataset file on disk" to "graph
  populated with the requested number of accounts and transactions" in a single command, with
  a printed summary at the end.
- **SC-003**: A developer can limit a PaySim load to a specific row count (e.g., 10,000 or
  1,000,000) and the resulting graph reflects exactly that scope.
- **SC-004**: Re-running either loader against the same source file does not create duplicate
  entities.
- **SC-005**: A load run involving invalid rows completes successfully overall, with every
  invalid row individually accounted for in the summary rather than silently dropped or
  causing the whole run to fail.
- **SC-006**: Loading the full PaySim dataset (~6.3 million rows) completes without the
  loader running out of memory.
- **SC-007**: A developer loading the full PaySim dataset does not have to wait for millions
  of individual round-trips to the system — the load completes using a small number of
  bulk requests instead of one request per record.

## Assumptions

- The developer has already downloaded the Elliptic and/or PaySim dataset files to local
  disk; the loaders read local files and do not themselves fetch data from Kaggle or any
  other external source (which would require handling that source's own authentication/terms
  of service).
- Each loader ensures its own required entity/relationship type definitions exist (creating
  them if absent) rather than requiring a separate manual setup step first.
- Duplicate detection applies to entities (per the target system's existing identifying-
  property rule) but not to relationships in this feature; re-running a loader will not
  duplicate accounts/transactions-as-entities, but may create duplicate flow/transaction
  relationships if run twice against the same rows. Removing that limitation is out of scope
  for this feature.
- Default row-count limit, batch size, and file locations are configuration details decided
  during planning, not fixed here; a "load everything" option is always available alongside
  any limit.
- These loaders are developer/demo tooling, not a production data-ingestion pipeline; they
  are not expected to support scheduled/recurring imports, incremental updates, or rollback of
  a partial load.
- This feature depends on the already-implemented entity/relationship CRUD and schema API
  (feature 001) and extends it with one addition: a bulk-import endpoint. It does not change
  any existing single-record endpoint's behavior.
- The Elliptic dataset's ~165 anonymized/PCA-transformed numeric features are not loaded as
  entity properties; only the id, time step, and class label are kept, since the raw features
  are meaningless without Elliptic's private feature dictionary and aren't needed for the
  traversal/graph-algorithm use cases this dataset supports.
