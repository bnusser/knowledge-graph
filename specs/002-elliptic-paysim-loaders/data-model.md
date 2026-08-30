# Phase 1 Data Model: Elliptic + PaySim Dataset Loaders

This feature introduces no new Neo4j node/relationship *types* at the persistence layer — it
reuses feature 001's generic `Entity`/`Relationship` model, registering new schema
definitions (`EntityTypeDefinition`/`RelationshipTypeDefinition`) for the two datasets, plus
new bulk request/response DTOs on the Java side.

## Schema definitions registered by the Elliptic loader

**EntityTypeDefinition `Transaction`**

| Property | Type | Required | Notes |
|---|---|---|---|
| txId | STRING | yes (identifying) | Elliptic's original transaction id |
| timeStep | INTEGER | yes | Elliptic's 1-49 time step |
| class | STRING | yes | `licit`, `illicit`, or `unknown` |
| dataset | STRING | yes | Constant `elliptic`; identifies the source dataset |

**RelationshipTypeDefinition `FLOWS_TO`**

| Field | Value |
|---|---|
| allowedSourceTypes | `["Transaction"]` |
| allowedTargetTypes | `["Transaction"]` |
| properties | `dataset` (STRING, required; constant `elliptic`) |

## Schema definitions registered by the PaySim loader

**EntityTypeDefinition `Account`**

| Property | Type | Required | Notes |
|---|---|---|---|
| name | STRING | yes (identifying) | Raw `nameOrig`/`nameDest` value |
| dataset | STRING | yes | Constant `paysim`; identifies the source dataset |

**RelationshipTypeDefinition `TRANSACTION`**

| Field | Value |
|---|---|
| allowedSourceTypes | `["Account"]` |
| allowedTargetTypes | `["Account"]` |
| properties | `step` (INTEGER), `transactionType` (STRING; source CSV column `type`), `amount` (FLOAT), `oldBalanceOrig` (FLOAT), `newBalanceOrig` (FLOAT), `oldBalanceDest` (FLOAT), `newBalanceDest` (FLOAT), `isFraud` (BOOLEAN), `isFlaggedFraud` (BOOLEAN), `dataset` (STRING; constant `paysim`) |

`transactionType` deliberately avoids the reserved persistence property `type`, which stores
the graph relationship discriminator (`TRANSACTION`).

## New Java-side DTOs (bulk endpoints)

**BulkEntityCreateRequest**: `{ "items": [EntityCreateRequest, ...] }` (reuses feature 001's
existing `EntityCreateRequest` shape per item).

**BulkRelationshipCreateRequest**: `{ "items": [RelationshipCreateRequest, ...] }` (reuses
feature 001's existing `RelationshipCreateRequest` shape per item).

**BulkItemResult**: `{ "index": int, "status": "created"|"already_present"|"rejected", "id": string|null, "error": string|null }`
— one per submitted item, in submission order, satisfying FR-012's per-record outcome
requirement.

## Loader-side (Python) data shapes

**LoadSummary**: `created: int`, `already_present: int`, `skipped: int`,
`skip_reasons: list[str]`, `elapsed_seconds: float` — printed at the end of every loader run
(FR-008).

**Validation rules** (from spec Functional Requirements):
- A row referencing a transaction/account id not resolvable within the current dataset is
  skipped with a reason (FR-006), not submitted to the API.
- A row-count limit, when set, takes the first N rows of the source file (deterministic,
  per clarification) — FR-005.
