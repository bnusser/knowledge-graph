# Quickstart: Elliptic + PaySim Dataset Loaders

## Prerequisites

- `java-core` running locally (see [../001-entity-crud-schema/quickstart.md](../001-entity-crud-schema/quickstart.md))
  with the bulk-import endpoints from this feature deployed.
- Python 3.12+ and `uv`.
- Downloaded dataset files:
  - Elliptic: `elliptic_txs_features.csv`, `elliptic_txs_edgelist.csv`, `elliptic_txs_classes.csv`
  - PaySim: the single PaySim CSV (e.g., `PS_20174392719_1491204439457_log.csv`)

## Install

```powershell
cd loaders
uv sync
```

## Run the Elliptic loader

```powershell
uv run python -m kg_loaders.elliptic_loader `
  --nodes ..\data\elliptic_bitcoin_dataset\elliptic_txs_features.csv `
  --edges ..\data\elliptic_bitcoin_dataset\elliptic_txs_edgelist.csv `
  --classes ..\data\elliptic_bitcoin_dataset\elliptic_txs_classes.csv `
  --api-url http://127.0.0.1:8080 `
  --api-key local-dev-key `
  --max-workers 8 `
  --timeout 60
```

Expected: a `Transaction` entity type and `FLOWS_TO` relationship type are registered (if not
already present), then all transactions and flows are loaded in batches, ending with a
summary like:

```text
Load Summary: created=203769 already_present=0 skipped=0 elapsed=42.3s
```

## Run the PaySim loader

```powershell
uv run python -m kg_loaders.paysim_loader `
  --input ..\data\paysim1\PS_20174392719_1491204439457_log.csv `
  --api-url http://127.0.0.1:8080 `
  --api-key local-dev-key `
  --max-workers 8 `
  --timeout 60 `
  --limit 10000
```

`--limit` takes the first N rows (deterministic, per the spec's clarification) — omit it to
load the full file. Expected output: an `Account` entity type and `TRANSACTION` relationship
type registered, then a summary like:

```text
Load Summary: created=10000 already_present=312 skipped=0 elapsed=6.1s
```

## Validation scenarios

1. **Bulk endpoint partial-failure handling** (FR-012): submit a batch to
   `POST /entities/bulk` containing one entity with an invalid type and several valid ones →
   expect a `200` response whose `results` array shows `rejected` for the bad item and
   `created` for the rest.
2. **Re-run idempotency for entities** (SC-004): run either loader twice against the same
   file → the second run's summary shows the same `created` count moved into
   `already_present`, with no duplicate entities in the graph.
3. **Row-limit determinism** (FR-005): run the PaySim loader twice with `--limit 100` →
   both runs load the exact same 100 rows.
4. **Full-scale run** (SC-006/SC-007): run the PaySim loader without `--limit` against the
   canonical file. It contains 6,362,620 transaction rows and 9,073,900 distinct accounts.
   With batch size 500, a fresh load makes exactly 18,148 entity bulk requests plus 12,726
   relationship bulk requests (30,874 total), rather than millions. Confirm those graph
   counts, required `dataset=paysim` properties, and completion without an out-of-memory
   error. Account names and server IDs are held in a temporary disk-backed SQLite index.

`--max-workers` bounds concurrency: at most twice that many request batches are held in
flight. Use `1` for deterministic sequential troubleshooting; increase it only as far as the
local API and Neo4j instance can sustain.

Full request/response shapes are in [contracts/bulk-import.yaml](./contracts/bulk-import.yaml)
and schema definitions in [data-model.md](./data-model.md). Recorded automated and full-scale
results are in [validation.md](./validation.md).
