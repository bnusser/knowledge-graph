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
            {"name": "dataset", "dataType": "STRING", "required": True},
        ],
        "identifyingProperty": "txId",
    })
    client.ensure_relationship_type({
        "name": "FLOWS_TO",
        "allowedSourceTypes": ["Transaction"],
        "allowedTargetTypes": ["Transaction"],
        "properties": [{"name": "dataset", "dataType": "STRING", "required": True}],
    })


def _require_columns(path: Path, candidates: dict[str, tuple[str, ...]]) -> None:
    """Fail fast with a clear error if a source file's header doesn't match any expected name.

    `candidates` maps a logical column (e.g. "transaction id") to the header names accepted
    for it. Prevents silently skipping every row when the wrong file is passed to a flag.
    """
    with path.open(newline="", encoding="utf-8-sig") as source:
        header = next(csv.reader(source), [])
    missing = [
        logical_name
        for logical_name, accepted_headers in candidates.items()
        if not any(name in header for name in accepted_headers)
    ]
    if missing:
        raise ValueError(
            f"{path} is missing expected column(s) {missing} (found header: {header}); "
            "check that the correct dataset file was passed for this argument"
        )


def _iter_node_rows(path: Path) -> Iterable[tuple[str | None, str | None]]:
    """Yield (txId, timeStep) pairs from the Elliptic node/features file.

    The authentic Elliptic dataset ships this file with no header row at all (columns:
    txId, time step, then ~165 anonymized numeric features); some re-uploads add a header
    instead. Detect which format a given file uses from its first row rather than assuming one.
    """
    with path.open(newline="", encoding="utf-8-sig") as source:
        reader = csv.reader(source)
        first_row = next(reader, None)
        if first_row is None or len(first_row) < 2:
            raise ValueError(f"{path} has no data (expected at least txId and time step columns)")
        if _is_header_row(first_row):
            tx_index = _index_of(first_row, "transaction id", ("txId", "transactionId"))
            time_index = _index_of(first_row, "time step", ("time step", "timeStep"))
        else:
            tx_index, time_index = 0, 1
            yield (first_row[tx_index], first_row[time_index])
        for row in reader:
            if len(row) <= max(tx_index, time_index):
                yield (None, None)
                continue
            yield (row[tx_index], row[time_index])


def _is_header_row(row: list[str]) -> bool:
    """A header's second field (time step) is text; a headerless data row's is numeric."""
    try:
        int(row[1])
        return False
    except ValueError:
        return True


def _index_of(header: list[str], logical_name: str, accepted_names: tuple[str, ...]) -> int:
    for name in accepted_names:
        if name in header:
            return header.index(name)
    raise ValueError(
        f"missing expected column '{logical_name}' (found header: {header}); "
        "check that the correct dataset file was passed for this argument"
    )


def load(
    nodes_path: Path, edges_path: Path, classes_path: Path, client: ApiClient, skip_entities: bool = False
) -> LoadSummary:
    summary = LoadSummary()
    _ensure_schema(client)
    _require_columns(classes_path, {"transaction id": ("txId", "txId1", "transactionId")})
    _require_columns(edges_path, {"source id": ("txId1", "source", "sourceId"), "target id": ("txId2", "target", "targetId")})
    classes: dict[str, str] = {}
    with classes_path.open(newline="", encoding="utf-8-sig") as source:
        for row in csv.DictReader(source):
            tx_id = row.get("txId") or row.get("txId1") or row.get("transactionId")
            if tx_id:
                classes[tx_id] = row.get("class", "unknown") or "unknown"

    entity_ids: dict[str, str] = {}
    submitted_ids: list[str] = []
    def entities() -> Iterable[dict[str, object]]:
        for tx_id, time_step in _iter_node_rows(nodes_path):
            if not tx_id or time_step is None:
                summary.skip("malformed Elliptic node row")
                continue
            try:
                parsed_time_step = int(time_step)
            except ValueError:
                summary.skip(f"invalid time step for transaction {tx_id}")
                continue
            submitted_ids.append(tx_id)
            yield {"type": "Transaction", "properties": {
                "txId": tx_id, "timeStep": parsed_time_step, "class": classes.get(tx_id, "unknown"),
                "dataset": "elliptic",
            }}

    try:
        if skip_entities:
            # Entities already loaded: one read instead of re-submitting ~all of them just to
            # get "already_present" back. Builds the same txId -> Neo4j id mapping the
            # relationships phase needs, from what's already in the graph.
            for existing in client.get("/entities?type=Transaction"):
                tx_id = existing.get("properties", {}).get("txId")
                if tx_id and existing.get("id"):
                    entity_ids[tx_id] = str(existing["id"])
            print(f"[elliptic_loader] Skipped entity load; fetched {len(entity_ids)} existing Transaction entities.")
        else:
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
                           "targetEntityId": entity_ids[target_id], "properties": {"dataset": "elliptic"}}

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
    parser.add_argument("--max-workers", type=int, default=1, help="concurrent bulk-request workers (default: sequential)")
    parser.add_argument("--timeout", type=float, default=30.0, help="per-request HTTP timeout in seconds (default: 30)")
    parser.add_argument("--skip-entities", action="store_true",
                         help="skip loading entities; fetch existing Transaction entities instead (use when they're already fully loaded)")
    args = parser.parse_args()
    load(args.nodes, args.edges, args.classes,
         ApiClient(args.api_url, args.api_key, args.batch_size, timeout=args.timeout, max_workers=args.max_workers),
         skip_entities=args.skip_entities)


if __name__ == "__main__":
    main()
