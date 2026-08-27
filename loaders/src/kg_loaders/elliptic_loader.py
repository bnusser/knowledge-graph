"""Load Elliptic transaction nodes, classes, and directed flows."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Iterable

from .api_client import ApiClient, ApiClientError
from .summary import LoadSummary


def _ensure_schema(client: ApiClient) -> None:
    client.ensure_entity_type({
        "name": "Transaction",
        "properties": [
            {"name": "txId", "dataType": "STRING", "required": True},
            {"name": "timeStep", "dataType": "INTEGER", "required": True},
            {"name": "class", "dataType": "STRING", "required": True},
        ],
        "identifyingProperty": "txId",
    })
    client.ensure_relationship_type({
        "name": "FLOWS_TO",
        "allowedSourceTypes": ["Transaction"],
        "allowedTargetTypes": ["Transaction"],
        "properties": [],
    })


def load(nodes_path: Path, edges_path: Path, classes_path: Path, client: ApiClient) -> LoadSummary:
    summary = LoadSummary()
    _ensure_schema(client)
    classes: dict[str, str] = {}
    with classes_path.open(newline="", encoding="utf-8-sig") as source:
        for row in csv.DictReader(source):
            tx_id = row.get("txId") or row.get("txId1") or row.get("transactionId")
            if tx_id:
                classes[tx_id] = row.get("class", "unknown") or "unknown"

    entity_ids: dict[str, str] = {}
    submitted_ids: list[str] = []
    def entities() -> Iterable[dict[str, object]]:
        with nodes_path.open(newline="", encoding="utf-8-sig") as source:
            for row in csv.DictReader(source):
                tx_id = row.get("txId") or row.get("transactionId")
                time_step = row.get("time step") or row.get("timeStep")
                if not tx_id or time_step is None:
                    summary.skip("malformed Elliptic node row")
                    continue
                try:
                    submitted_ids.append(tx_id)
                    yield {"type": "Transaction", "properties": {
                        "txId": tx_id, "timeStep": int(time_step), "class": classes.get(tx_id, "unknown")
                    }}
                except ValueError:
                    summary.skip(f"invalid time step for transaction {tx_id}")

    try:
        results = client.send_entities(entities())
        summary.add_results(results)
        for tx_id, result in zip(submitted_ids, results):
            if result.get("status") in {"created", "already_present"} and result.get("id"):
                entity_ids[tx_id] = str(result["id"])

        def relationships() -> Iterable[dict[str, object]]:
            with edges_path.open(newline="", encoding="utf-8-sig") as source:
                for row in csv.DictReader(source):
                    source_id = row.get("txId1") or row.get("source") or row.get("sourceId")
                    target_id = row.get("txId2") or row.get("target") or row.get("targetId")
                    if not source_id or not target_id or source_id not in entity_ids or target_id not in entity_ids:
                        summary.skip("Elliptic edge references an unknown transaction")
                        continue
                    yield {"type": "FLOWS_TO", "sourceEntityId": entity_ids[source_id],
                           "targetEntityId": entity_ids[target_id], "properties": {}}

        summary.add_results(client.send_relationships(relationships()))
    except ApiClientError as error:
        print(f"Elliptic load stopped: {error}")
    summary.print()
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--nodes", type=Path, required=True)
    parser.add_argument("--edges", type=Path, required=True)
    parser.add_argument("--classes", type=Path, required=True)
    parser.add_argument("--api-url", required=True)
    parser.add_argument("--api-key", required=True)
    parser.add_argument("--batch-size", type=int, default=500)
    args = parser.parse_args()
    load(args.nodes, args.edges, args.classes, ApiClient(args.api_url, args.api_key, args.batch_size))


if __name__ == "__main__":
    main()
