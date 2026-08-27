# Phase 0 Research: Elliptic + PaySim Dataset Loaders

## Decision: Loader language = Python 3.12+ with `uv`

**Rationale**: The constitution already pins Python 3.12+/`uv` as the standard for the
(future) LangGraph agent layer; reusing it here avoids introducing a third language/toolchain
just for data-loading scripts, and Python's CSV/TSV handling and scripting ergonomics are a
natural fit for this kind of ETL tooling.

**Alternatives considered**: A Java CLI module inside `java-core` — rejected to keep the
loaders decoupled from the core's build/release cycle and because Python is simply less
verbose for this kind of one-off file-parsing tool.

## Decision: HTTP client = `requests`

**Rationale**: Batches are sent sequentially (no concurrency requirement was identified),
so a simple synchronous client is sufficient. `requests` is the most widely understood choice
and is trivial to mock in tests via the `responses` library.

**Alternatives considered**: `httpx` — offers async support, which isn't needed since this
feature doesn't require concurrent batch submission; adds complexity without benefit here.

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

## Decision: Partial-batch semantics = one Neo4j transaction per item within a batch

**Rationale**: Directly satisfies FR-012 — a single invalid record can't roll back or block
the rest of the batch, since each item's validation and persistence happens independently
using the same logic (and same exceptions → status mapping) as today's single-record
endpoints.

**Alternatives considered**: One transaction for the whole batch — rejected because a single
bad record would roll back every valid record in that batch, violating FR-012.

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
- Relationship type `TRANSACTION`: properties `step`, `type`, `amount`, `oldBalanceOrig`,
  `newBalanceOrig`, `oldBalanceDest`, `newBalanceDest`, `isFraud`, `isFlaggedFraud` — a
  direct, low-friction mapping of PaySim's own columns.

**Rationale**: PaySim's columns already map cleanly onto the existing schema model; no
transformation beyond type coercion (e.g., string "1"/"0" → boolean) is needed.

## Decision: Duplicate accounts handled by the bulk endpoint's existing semantics, not client-side

**Rationale**: FR-007 already requires the (bulk) entity endpoint to treat an existing entity
as already-present rather than an error. The loader can therefore submit every account
encountered per batch without pre-checking for duplicates itself, keeping the loader simple.

**Alternatives considered**: Client-side deduplication (loader tracks seen account names in
memory) — rejected as unnecessary extra state; the server already handles it correctly.
