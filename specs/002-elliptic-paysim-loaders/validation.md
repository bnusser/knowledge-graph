# Feature 002 Validation

Validation date: 2026-08-30 (local Docker Compose stack)

## Automated validation

- `loaders`: `21 passed, 1 deselected` in the default fast pytest suite.
- Full synthetic PaySim scale test: `1 passed in 1596.65s`; it exercised 6,362,620 rows,
  remained below the asserted 256 MiB Python allocation ceiling, and verified exactly 25,451
  entity requests plus 12,726 relationship requests for its worst-case 12,725,240 unique
  synthetic account names.
- `java-core`: `mvn verify` passed, including unit tests and Testcontainers integration tests
  for partial success, duplicate identifiers, authentication, and large bulk outcomes.
- Canonical OpenAPI YAML parsed successfully and contains both bulk paths and all four bulk
  request/result schemas.
- Bulk repository audit found only parameterized Cypher (`$type`, `$identifyingValues`, and
  `$rows`); no dataset values are interpolated into query text.

## Real PaySim full-load validation

Source: `data/paysim1/PS_20174392719_1491204439457_log.csv`

- CSV transaction rows: 6,362,620
- Distinct `nameOrig`/`nameDest` accounts: 9,073,900
- Starting graph: 1,276,000 Account entities and 0 TRANSACTION relationships
- Command: documented in `loaders/README.md` and `quickstart.md`, default batch size 500,
  `--max-workers 8`, `--timeout 60`
- Expected bulk requests: 18,148 entity + 12,726 relationship = 30,874
- Expected resumed summary: created=14,160,520, already_present=1,276,000, skipped=0
- Observed loader peak working set during indexing, account writes, and relationship writes:
  61 MiB
- Observed summary: `created=14160520 already_present=1276000 skipped=0
  elapsed=1489.7s` (24m 49.7s)
- Final Account count: 9,073,900; all 9,073,900 have `dataset=paysim` and `name`
- Final TRANSACTION count: 6,362,620; all 6,362,620 have `dataset=paysim`, `step`,
  `transactionType`, `amount`, both origin balances, both destination balances, `isFraud`,
  and `isFlaggedFraud`

The first property scan exposed a reserved-name collision: the source CSV's `type` value had
overwritten the core relationship discriminator. The mapping is now `type` (CSV) to
`transactionType` (graph property), repository writes restore core `id`/`type` after applying
dynamic properties, an integration regression test covers this invariant, and all 6,362,620
loaded relationships plus the `TRANSACTION` schema definition were migrated before the final
counts above were recorded.
