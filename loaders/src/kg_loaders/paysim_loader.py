"""Load PaySim accounts and transaction relationships."""

from __future__ import annotations

import argparse
import csv
import sqlite3
import tempfile
from contextlib import closing
from pathlib import Path
from typing import Iterable, Iterator

from .api_client import ApiClient, ApiClientError
from .summary import LoadSummary


BOOLEAN_FIELDS = {"isFraud", "isFlaggedFraud"}
CSV_FIELDS = {
    "step",
    "type",
    "amount",
    "nameOrig",
    "oldbalanceOrg",
    "newbalanceOrig",
    "nameDest",
    "oldbalanceDest",
    "newbalanceDest",
    "isFraud",
    "isFlaggedFraud",
}
NUMBER_FIELDS = {
    "step": ("step", int),
    "amount": ("amount", float),
    "oldBalanceOrig": ("oldbalanceOrg", float),
    "newBalanceOrig": ("newbalanceOrig", float),
    "oldBalanceDest": ("oldbalanceDest", float),
    "newBalanceDest": ("newbalanceDest", float),
}


def _boolean(value: str) -> bool:
    if value.strip().lower() in {"1", "true", "yes"}:
        return True
    if value.strip().lower() in {"0", "false", "no"}:
        return False
    raise ValueError(f"invalid boolean value {value!r}")


def _ensure_schema(client: ApiClient) -> None:
    client.ensure_entity_type({"name": "Account", "properties": [
        {"name": "name", "dataType": "STRING", "required": True},
        {"name": "dataset", "dataType": "STRING", "required": True}], "identifyingProperty": "name"})
    client.ensure_relationship_type({"name": "TRANSACTION", "allowedSourceTypes": ["Account"],
        "allowedTargetTypes": ["Account"], "properties": [
            {"name": name, "dataType": "BOOLEAN" if name in BOOLEAN_FIELDS else
            "INTEGER" if name == "step" else "FLOAT" if name != "transactionType" else "STRING", "required": True}
            for name in ["step", "transactionType", "amount", "oldBalanceOrig", "newBalanceOrig",
                         "oldBalanceDest", "newBalanceDest", "isFraud", "isFlaggedFraud"]]
            + [{"name": "dataset", "dataType": "STRING", "required": True}]})


def _validate_input(input_path: Path) -> None:
    with input_path.open(newline="", encoding="utf-8-sig") as source:
        reader = csv.DictReader(source)
        header = set(reader.fieldnames or [])
    missing = sorted(CSV_FIELDS - header)
    if missing:
        raise ValueError(
            f"{input_path} is missing expected column(s) {missing} "
            f"(found header: {sorted(header)})"
        )


def _rows(input_path: Path, limit: int | None) -> Iterator[dict[str, str]]:
    with input_path.open(newline="", encoding="utf-8-sig") as source:
        for row_number, row in enumerate(csv.DictReader(source)):
            if limit is not None and row_number >= limit:
                break
            yield row


def _index_accounts(
    input_path: Path,
    limit: int | None,
    connection: sqlite3.Connection,
    insert_batch_size: int = 10_000,
) -> None:
    pending: list[tuple[str]] = []
    for row in _rows(input_path, limit):
        for field in ("nameOrig", "nameDest"):
            name = row.get(field, "").strip()
            if name:
                pending.append((name,))
        if len(pending) >= insert_batch_size:
            connection.executemany("INSERT OR IGNORE INTO accounts(name) VALUES (?)", pending)
            pending.clear()
    if pending:
        connection.executemany("INSERT OR IGNORE INTO accounts(name) VALUES (?)", pending)
    connection.commit()


def _resolved_relationships(
    input_path: Path,
    limit: int | None,
    connection: sqlite3.Connection,
    summary: LoadSummary,
    lookup_batch_size: int,
) -> Iterator[dict[str, object]]:
    pending: list[dict[str, str]] = []

    def resolve(rows: list[dict[str, str]]) -> Iterator[dict[str, object]]:
        names = {
            row.get(field, "").strip()
            for row in rows
            for field in ("nameOrig", "nameDest")
            if row.get(field, "").strip()
        }
        account_ids: dict[str, str] = {}
        name_list = list(names)
        # Keep below SQLite builds whose host-parameter limit is 999.
        for start in range(0, len(name_list), 900):
            chunk = name_list[start:start + 900]
            placeholders = ",".join("?" for _ in chunk)
            query = f"SELECT name, entity_id FROM accounts WHERE name IN ({placeholders})"
            account_ids.update(
                (name, entity_id)
                for name, entity_id in connection.execute(query, chunk)
                if entity_id
            )

        for row in rows:
            try:
                source_name = row["nameOrig"].strip()
                target_name = row["nameDest"].strip()
                if source_name not in account_ids or target_name not in account_ids:
                    summary.skip("PaySim row references an unknown account")
                    continue
                properties: dict[str, object] = {
                    # `type` is the core relationship discriminator in Neo4j; keep the
                    # PaySim transaction category under a non-reserved property name.
                    "transactionType": row["type"],
                    "dataset": "paysim",
                }
                for field, (source_field, converter) in NUMBER_FIELDS.items():
                    properties[field] = converter(row[source_field])
                for field in BOOLEAN_FIELDS:
                    properties[field] = _boolean(row[field])
                yield {
                    "type": "TRANSACTION",
                    "sourceEntityId": account_ids[source_name],
                    "targetEntityId": account_ids[target_name],
                    "properties": properties,
                }
            except (KeyError, ValueError) as error:
                summary.skip(f"malformed PaySim row: {error}")

    for row in _rows(input_path, limit):
        pending.append(row)
        if len(pending) == lookup_batch_size:
            yield from resolve(pending)
            pending.clear()
    if pending:
        yield from resolve(pending)


def load(input_path: Path, client: ApiClient, limit: int | None = None) -> LoadSummary:
    summary = LoadSummary()
    _validate_input(input_path)
    _ensure_schema(client)
    lookup_batch_size = max(1, int(getattr(client, "batch_size", 500)))

    with tempfile.TemporaryDirectory(prefix="kg-paysim-") as work_directory:
        database_path = Path(work_directory) / "accounts.sqlite3"
        with closing(sqlite3.connect(database_path)) as connection:
            connection.execute(
                "CREATE TABLE accounts ("
                "sequence INTEGER PRIMARY KEY AUTOINCREMENT, "
                "name TEXT NOT NULL UNIQUE, "
                "entity_id TEXT)"
            )
            _index_accounts(input_path, limit, connection)

            def accounts() -> Iterable[dict[str, object]]:
                for (name,) in connection.execute("SELECT name FROM accounts ORDER BY sequence"):
                    yield {
                        "type": "Account",
                        "properties": {"name": name, "dataset": "paysim"},
                    }

            def record_accounts(
                records: list[dict[str, object]], results: list[dict[str, object]]
            ) -> None:
                summary.add_results(results)
                updates: list[tuple[str, str]] = []
                for result in results:
                    index = int(result.get("index", -1))
                    if not (0 <= index < len(records)):
                        continue
                    entity_id = result.get("id")
                    if entity_id and result.get("status") in {"created", "already_present"}:
                        name = str(records[index]["properties"]["name"])
                        updates.append((str(entity_id), name))
                if updates:
                    connection.executemany(
                        "UPDATE accounts SET entity_id = ? WHERE name = ?",
                        updates,
                    )
                    connection.commit()

            try:
                client.send_entities(
                    accounts(),
                    result_handler=record_accounts,
                    collect_results=False,
                )

                def record_relationships(
                    _records: list[dict[str, object]], results: list[dict[str, object]]
                ) -> None:
                    summary.add_results(results)

                client.send_relationships(
                    _resolved_relationships(
                        input_path,
                        limit,
                        connection,
                        summary,
                        lookup_batch_size,
                    ),
                    result_handler=record_relationships,
                    collect_results=False,
                )
            except ApiClientError as error:
                print(f"PaySim load stopped: {error}")
    summary.print()
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--api-url", required=True)
    parser.add_argument("--api-key", required=True)
    parser.add_argument("--batch-size", type=int, default=500)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--max-workers", type=int, default=1, help="concurrent bulk-request workers (default: sequential)")
    parser.add_argument("--timeout", type=float, default=30.0, help="per-request HTTP timeout in seconds (default: 30)")
    args = parser.parse_args()
    load(args.input,
         ApiClient(args.api_url, args.api_key, args.batch_size, timeout=args.timeout, max_workers=args.max_workers),
         args.limit)


if __name__ == "__main__":
    main()
