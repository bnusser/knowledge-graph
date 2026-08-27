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
  --nodes path\to\elliptic_txs_features.csv `
  --edges path\to\elliptic_txs_edgelist.csv `
  --classes path\to\elliptic_txs_classes.csv `
  --api-url http://127.0.0.1:8080 `
  --api-key local-dev-key
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
  --input path\to\PS_20174392719_1491204439457_log.csv `
  --api-url http://127.0.0.1:8080 `
  --api-key local-dev-key `
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
   full ~6.3M-row file and confirm it completes without an out-of-memory error, using on the
   order of thousands (not millions) of HTTP requests.

Full request/response shapes are in [contracts/bulk-import.yaml](./contracts/bulk-import.yaml)
and schema definitions in [data-model.md](./data-model.md).
