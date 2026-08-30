# Phase 0 Research: Elliptic + PaySim Dataset Loaders

## Decision: Loader language = Python 3.12+ with `uv`

**Rationale**: The constitution already pins Python 3.12+/`uv` as the standard for the
(future) LangGraph agent layer; reusing it here avoids introducing a third language/toolchain
just for data-loading scripts, and Python's CSV/TSV handling and scripting ergonomics are a
natural fit for this kind of ETL tooling.

**Alternatives considered**: A Java CLI module inside `java-core` — rejected to keep the
loaders decoupled from the core's build/release cycle and because Python is simply less
verbose for this kind of one-off file-parsing tool.

## Decision: HTTP client = `requests` with bounded worker concurrency

**Rationale**: `requests` remains simple and readily testable, while `--max-workers` permits
parallel batch submission when the local API and database can benefit from it. The client
keeps at most `max_workers * 2` batches in flight, uses one Session per worker, and consumes
completed results incrementally. Entity batches are drained in submission order because the
loader must correlate each result to its account; relationship batches may drain in
completion order. If a request fails, queued work is cancelled and the error reports only
the count from batches whose results were consumed successfully.

**Alternatives considered**: Sequential-only `requests` remains available with
`--max-workers 1`, but is too slow for full PaySim validation. An async `httpx` client was
rejected because a bounded thread pool provides the needed throughput without a second
concurrency model.

## Decision: Bulk endpoints = `POST /entities/bulk` and `POST /relationships/bulk`, added to the existing controllers

**Rationale**: Entities must exist before relationships can reference them, so a loader
naturally performs two phases per batch (bulk-create entities, then bulk-create
relationships) — two separate endpoints mirror that ordering and reuse each existing
service's per-record validation logic via a simple loop, rather than introducing a new
unified "generic bulk" endpoint that would need its own dispatch logic for mixed record
types.

**Alternatives considered**: A single `POST /bulk-import` endpoint accepting a mixed array of
entities and relationships — rejected as it would need internal ordering/dependency logic
(create referenced entities before relationships within the same payload) that two ordered
calls avoid entirely.

## Decision: Batch size = 500 records per request (configurable)

**Rationale**: Cuts a 6.3M-row PaySim load from ~6.3M requests to ~12,600 — enough to satisfy
SC-007 — while keeping individual request/response payloads and per-item transactions
reasonably sized. Made configurable (loader CLI flag) since the right number depends on
network conditions and isn't worth hard-coding.

**Alternatives considered**: A single request for the entire file — rejected; risks very
large request bodies and makes partial-failure reporting (FR-012) coarser (harder to isolate
which of millions of records failed).

## Decision: Bulk transaction first, isolated fallback on write failure

**Rationale**: Requests are validated item-by-item before persistence. Valid entities are
looked up by type in bulk and valid records are written in one parameterized batch query.
Valid relationships similarly resolve all referenced entities in one lookup and use one
batch write. If a database-level batch write fails, the service retries those prepared items
individually so one conflicting or rejected item cannot discard the rest (FR-012). This
keeps the normal path fast while preserving a result for every submitted item.

**Alternatives considered**: One Neo4j transaction per item in every request preserves
isolation but produces hundreds of database round-trips for each HTTP batch and does not
meet full-scale throughput needs.

## Decision: Elliptic schema mapping keeps only classification-relevant fields

**Rationale**: Elliptic's ~165 numeric transaction features are anonymized/PCA-transformed
and meaningless without Elliptic's private feature dictionary. Keeping only `timeStep` and
`class` (licit/illicit/unknown) is sufficient for the traversal and graph-algorithm demos
this dataset supports (see prior conversation on Elliptic's analytics use cases), and keeps
the schema simple and explainable.

**Alternatives considered**: Storing all ~165 anonymized features as opaque properties —
rejected; it would bloat every entity with data that can't be meaningfully explained or
validated, undermining Constitution Principle V (explainability).

## Decision: PaySim schema mapping

- Entity type `Account`: identifying property `name` (the raw `nameOrig`/`nameDest` value).
- Relationship type `TRANSACTION`: properties `step`, `transactionType` (mapped from the CSV
  column `type`), `amount`, `oldBalanceOrig`,
  `newBalanceOrig`, `oldBalanceDest`, `newBalanceDest`, `isFraud`, `isFlaggedFraud` — a
  direct, low-friction mapping of PaySim's own columns.

**Rationale**: PaySim's columns map cleanly onto the existing schema model. The CSV `type`
column is renamed to `transactionType` because `type` is reserved for the core relationship
discriminator; other transformations are type coercions (e.g., string "1"/"0" → boolean).

## Decision: PaySim account identity is indexed in a temporary SQLite database

**Rationale**: FR-007 still makes the server authoritative for existing-entity outcomes, but
the relationship phase needs every account's server ID. A full PaySim file contains
9,073,900 distinct names, so retaining names, results, and IDs in Python dictionaries is not
bounded-memory. The loader makes a streamed first pass into a temporary SQLite unique index,
submits each distinct account once, stores returned IDs on disk, then resolves relationship
endpoints in batch-sized windows. The temporary database is deleted after the run.

**Alternatives considered**: Submitting every repeated account avoids an index but adds many
unnecessary records and still requires an in-memory ID map. A global Python `set`/`dict` was
rejected because memory grows with the dataset.
