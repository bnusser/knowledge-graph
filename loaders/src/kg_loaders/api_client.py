"""HTTP client for the Java core bulk-import API."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Iterable

import requests


@dataclass
class ApiClientError(RuntimeError):
    message: str
    processed_count: int = 0

    def __str__(self) -> str:
        return f"{self.message} (processed={self.processed_count})"


class ApiClient:
    def __init__(
        self,
        base_url: str,
        api_key: str,
        batch_size: int = 500,
        timeout: float = 30.0,
        session: requests.Session | None = None,
    ) -> None:
        if batch_size < 1:
            raise ValueError("batch_size must be positive")
        self.base_url = base_url.rstrip("/")
        self.batch_size = batch_size
        self.timeout = timeout
        self.session = session or requests.Session()
        self.session.headers.update({"X-API-Key": api_key})

    def send_entities(self, records: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
        return self._send("/entities/bulk", records)

    def send_relationships(self, records: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
        return self._send("/relationships/bulk", records)

    def ensure_entity_type(self, definition: dict[str, Any]) -> None:
        self._ensure_type("/entity-types", definition)

    def ensure_relationship_type(self, definition: dict[str, Any]) -> None:
        self._ensure_type("/relationship-types", definition)

    def _ensure_type(self, path: str, definition: dict[str, Any]) -> None:
        try:
            existing = self.get(path)
            if any(item.get("name") == definition["name"] for item in existing):
                return
            self.post(path, definition)
        except ApiClientError as error:
            raise error

    def get(self, path: str) -> list[dict[str, Any]]:
        try:
            response = self.session.get(self.base_url + path, timeout=self.timeout)
            response.raise_for_status()
            return response.json()
        except (requests.RequestException, ValueError, TypeError) as error:
            raise ApiClientError(str(error)) from error

    def post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        try:
            response = self.session.post(self.base_url + path, json=payload, timeout=self.timeout)
            response.raise_for_status()
            return response.json()
        except (requests.RequestException, ValueError) as error:
            raise ApiClientError(str(error)) from error

    def _send(self, path: str, records: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
        results: list[dict[str, Any]] = []
        batch: list[dict[str, Any]] = []
        processed = 0
        for record in records:
            batch.append(record)
            if len(batch) == self.batch_size:
                results.extend(self._send_batch(path, batch, processed))
                processed += len(batch)
                batch = []
        if batch:
            results.extend(self._send_batch(path, batch, processed))
        return results

    def _send_batch(self, path: str, batch: list[dict[str, Any]], processed: int) -> list[dict[str, Any]]:
        try:
            response = self.session.post(self.base_url + path, json={"items": batch}, timeout=self.timeout)
            response.raise_for_status()
            body = response.json()
            return body["results"]
        except (requests.RequestException, ValueError, KeyError, TypeError) as error:
            raise ApiClientError(str(error), processed) from error
