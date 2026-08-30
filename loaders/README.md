# Dataset Loaders

Python 3.12+ command-line loaders for importing Elliptic and PaySim data through the Java
core's authenticated bulk API. Start Neo4j and `java-core` first, then install dependencies:

```powershell
cd loaders
uv sync
```

## Elliptic

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

## PaySim

```powershell
uv run python -m kg_loaders.paysim_loader `
  --input ..\data\paysim1\PS_20174392719_1491204439457_log.csv `
  --api-url http://127.0.0.1:8080 `
  --api-key local-dev-key `
  --max-workers 8 `
  --timeout 60
```

Add `--limit N` for a deterministic first-N-row test. The canonical full file contains
6,362,620 transactions and 9,073,900 distinct accounts. With the default batch size of 500,
a fresh full load sends 30,874 bulk requests. PaySim account identity and returned IDs are
kept in a temporary SQLite database, so Python memory does not grow with the account count.

## Tuning and troubleshooting

- Use `--max-workers 1` to diagnose failures sequentially. Concurrent mode keeps no more
  than `max-workers * 2` batches in flight.
- Increase `--timeout` when Neo4j is under sustained write load.
- A missing, unreadable, or structurally invalid input is rejected before schemas are
  registered or any records are written.
- Re-running a loader does not duplicate entities; relationship deduplication is outside
  this feature's scope, so a repeated complete run can create duplicate relationships.
- Every run prints created, already-present, skipped, skip reasons, and elapsed time.

See the [feature quickstart](../specs/002-elliptic-paysim-loaders/quickstart.md) for validation
scenarios and the [bulk API contract](../specs/001-entity-crud-schema/contracts/openapi.yaml)
for request and response shapes.
