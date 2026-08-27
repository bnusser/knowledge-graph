"""Load PaySim accounts and transaction relationships."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Iterable

from .api_client import ApiClient, ApiClientError
from .summary import LoadSummary


BOOLEAN_FIELDS = {"isFraud", "isFlaggedFraud"}
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
        {"name": "name", "dataType": "STRING", "required": True}], "identifyingProperty": "name"})
    client.ensure_relationship_type({"name": "TRANSACTION", "allowedSourceTypes": ["Account"],
        "allowedTargetTypes": ["Account"], "properties": [
            {"name": name, "dataType": "BOOLEAN" if name in BOOLEAN_FIELDS else
             "INTEGER" if name == "step" else "FLOAT" if name != "type" else "STRING", "required": True}
            for name in ["step", "type", "amount", "oldBalanceOrig", "newBalanceOrig",
                         "oldBalanceDest", "newBalanceDest", "isFraud", "isFlaggedFraud"]]})


def load(input_path: Path, client: ApiClient, limit: int | None = None) -> LoadSummary:
    summary = LoadSummary()
    _ensure_schema(client)
    account_ids: dict[str, str] = {}
    def accounts() -> Iterable[dict[str, object]]:
        seen: set[str] = set()
        with input_path.open(newline="", encoding="utf-8-sig") as source:
            for row_number, row in enumerate(csv.DictReader(source)):
                if limit is not None and row_number >= limit:
                    break
                for name_key in ("nameOrig", "nameDest"):
                    name = row.get(name_key, "").strip()
                    if name and name not in seen:
                        seen.add(name)
                        yield {"type": "Account", "properties": {"name": name}}

    try:
        account_results = client.send_entities(accounts())
        summary.add_results(account_results)
        account_names: list[str] = []
        with input_path.open(newline="", encoding="utf-8-sig") as source:
            seen: set[str] = set()
            for row_number, row in enumerate(csv.DictReader(source)):
                if limit is not None and row_number >= limit:
                    break
                for name_key in ("nameOrig", "nameDest"):
                    name = row.get(name_key, "").strip()
                    if name and name not in seen:
                        seen.add(name)
                        account_names.append(name)
        for name, result in zip(account_names, account_results):
            if result.get("id") and result.get("status") in {"created", "already_present"}:
                account_ids[name] = str(result["id"])

        def relationships() -> Iterable[dict[str, object]]:
            with input_path.open(newline="", encoding="utf-8-sig") as source:
                for row_number, row in enumerate(csv.DictReader(source)):
                    if limit is not None and row_number >= limit:
                        break
                    try:
                        source_name, target_name = row["nameOrig"].strip(), row["nameDest"].strip()
                        if source_name not in account_ids or target_name not in account_ids:
                            summary.skip("PaySim row references an unknown account")
                            continue
                        properties: dict[str, object] = {"type": row["type"]}
                        for field, (source_field, converter) in NUMBER_FIELDS.items():
                            properties[field] = converter(row[source_field])
                        for field in BOOLEAN_FIELDS:
                            properties[field] = _boolean(row[field])
                        yield {"type": "TRANSACTION", "sourceEntityId": account_ids[source_name],
                               "targetEntityId": account_ids[target_name], "properties": properties}
                    except (KeyError, ValueError) as error:
                        summary.skip(f"malformed PaySim row: {error}")

        summary.add_results(client.send_relationships(relationships()))
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
    args = parser.parse_args()
    load(args.input, ApiClient(args.api_url, args.api_key, args.batch_size), args.limit)


if __name__ == "__main__":
    main()
