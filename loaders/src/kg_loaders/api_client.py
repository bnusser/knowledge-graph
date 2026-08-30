"""HTTP client for the Java core bulk-import API."""

from __future__ import annotations

import threading
import time
from collections import deque
from concurrent.futures import Future, ThreadPoolExecutor
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
        max_workers: int = 1,
        progress: bool = True,
    ) -> None:
        if batch_size < 1:
            raise ValueError("batch_size must be positive")
        if max_workers < 1:
            raise ValueError("max_workers must be positive")
        self.base_url = base_url.rstrip("/")
        self.batch_size = batch_size
        self.timeout = timeout
        self.session = session or requests.Session()
        self.session.headers.update({"X-API-Key": api_key})
        self.max_workers = max_workers
        self.progress = progress
        self._api_key = api_key
        self._thread_local = threading.local()

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
        if self.max_workers == 1:
            return self._send_sequential(path, records)
        return self._send_concurrent(path, records)

    def _log_progress(self, path: str, batch_len: int, batch_seconds: float, processed: int, start_time: float) -> None:
        if not self.progress:
            return
        overall_seconds = time.monotonic() - start_time
        overall_rate = processed / overall_seconds if overall_seconds > 0 else 0.0
        batch_rate = batch_len / batch_seconds if batch_seconds > 0 else 0.0
        print(
            f"[api_client] {path}: batch of {batch_len} in {batch_seconds:.2f}s ({batch_rate:.1f} rec/s) | "
            f"total processed={processed} elapsed={overall_seconds:.1f}s avg={overall_rate:.1f} rec/s",
            flush=True,
        )

    def _send_sequential(self, path: str, records: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
        results: list[dict[str, Any]] = []
        batch: list[dict[str, Any]] = []
        processed = 0
        start_time = time.monotonic()
        for record in records:
            batch.append(record)
            if len(batch) == self.batch_size:
                batch_start = time.monotonic()
                results.extend(self._send_batch(path, batch, processed))
                processed += len(batch)
                self._log_progress(path, len(batch), time.monotonic() - batch_start, processed, start_time)
                batch = []
        if batch:
            batch_start = time.monotonic()
            results.extend(self._send_batch(path, batch, processed))
            processed += len(batch)
            self._log_progress(path, len(batch), time.monotonic() - batch_start, processed, start_time)
        return results

    def _send_concurrent(self, path: str, records: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
        """Dispatch batches to a thread pool while preserving submission order in the result.

        Keeps at most ``max_workers * 2`` batches in flight at once (a bounded sliding window,
        not "submit everything immediately") so memory stays bounded for very large files
        (FR-004), and drains completed futures strictly in submission order so each result's
        index still lines up with the record it belongs to.
        """
        results: list[dict[str, Any]] = []
        processed = 0
        max_in_flight = self.max_workers * 2
        window: deque[tuple[Future, int]] = deque()
        start_time = time.monotonic()

        def drain_one() -> None:
            nonlocal processed
            future, batch_len = window.popleft()
            try:
                batch_results, batch_seconds = future.result()
                results.extend(batch_results)
                processed += batch_len
                self._log_progress(path, batch_len, batch_seconds, processed, start_time)
            except ApiClientError as error:
                for pending_future, _ in window:
                    pending_future.cancel()
                raise ApiClientError(error.message, processed) from error

        with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
            batch: list[dict[str, Any]] = []
            for record in records:
                batch.append(record)
                if len(batch) == self.batch_size:
                    window.append((executor.submit(self._send_batch_threadsafe, path, batch), len(batch)))
                    batch = []
                    if len(window) >= max_in_flight:
                        drain_one()
            if batch:
                window.append((executor.submit(self._send_batch_threadsafe, path, batch), len(batch)))
            while window:
                drain_one()
        return results

    def _get_thread_session(self) -> requests.Session:
        """One requests.Session per worker thread — Session isn't documented as thread-safe."""
        session = getattr(self._thread_local, "session", None)
        if session is None:
            session = requests.Session()
            session.headers.update({"X-API-Key": self._api_key})
            self._thread_local.session = session
        return session

    def _send_batch_threadsafe(self, path: str, batch: list[dict[str, Any]]) -> tuple[list[dict[str, Any]], float]:
        start = time.monotonic()
        try:
            response = self._get_thread_session().post(
                self.base_url + path, json={"items": batch}, timeout=self.timeout
            )
            response.raise_for_status()
            return response.json()["results"], time.monotonic() - start
        except (requests.RequestException, ValueError, KeyError, TypeError) as error:
            raise ApiClientError(str(error)) from error

    def _send_batch(self, path: str, batch: list[dict[str, Any]], processed: int) -> list[dict[str, Any]]:
        try:
            response = self.session.post(self.base_url + path, json={"items": batch}, timeout=self.timeout)
            response.raise_for_status()
            body = response.json()
            return body["results"]
        except (requests.RequestException, ValueError, KeyError, TypeError) as error:
            raise ApiClientError(str(error), processed) from error
