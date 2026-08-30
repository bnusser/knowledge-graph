"""HTTP client for the Java core bulk-import API."""

from __future__ import annotations

import threading
import time
from collections import deque
from concurrent.futures import Future, ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from typing import Any, Callable, Iterable

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

    def send_entities(
        self,
        records: Iterable[dict[str, Any]],
        result_handler: Callable[[list[dict[str, Any]], list[dict[str, Any]]], None] | None = None,
        collect_results: bool = True,
    ) -> list[dict[str, Any]]:
        return self._send(
            "/entities/bulk",
            records,
            preserve_order=True,
            result_handler=result_handler,
            collect_results=collect_results,
        )

    def send_relationships(
        self,
        records: Iterable[dict[str, Any]],
        result_handler: Callable[[list[dict[str, Any]], list[dict[str, Any]]], None] | None = None,
        collect_results: bool = True,
    ) -> list[dict[str, Any]]:
        # Order doesn't need to be preserved here: nothing downstream indexes into relationship
        # results positionally (unlike entities, which correlate submitted_ids[i]/results[i] to
        # build an id map). Draining by completion order instead of submission order avoids
        # head-of-line blocking — one slow/unlucky batch no longer stalls all progress reporting
        # while later, faster batches have already finished.
        return self._send(
            "/relationships/bulk",
            records,
            preserve_order=False,
            result_handler=result_handler,
            collect_results=collect_results,
        )

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

    def _send(
        self,
        path: str,
        records: Iterable[dict[str, Any]],
        preserve_order: bool = True,
        result_handler: Callable[[list[dict[str, Any]], list[dict[str, Any]]], None] | None = None,
        collect_results: bool = True,
    ) -> list[dict[str, Any]]:
        if self.max_workers == 1:
            return self._send_sequential(path, records, result_handler, collect_results)
        return self._send_concurrent(path, records, preserve_order, result_handler, collect_results)

    def _log_progress(self, path: str, batch_len: int, batch_seconds: float, processed: int, start_time: float) -> None:
        if not self.progress:
            return
        # A full PaySim run sends tens of thousands of batches. Report the first batch and
        # then every 50 batches so console I/O does not become part of the scalability cost.
        if processed != batch_len and processed % (self.batch_size * 50) != 0:
            return
        overall_seconds = time.monotonic() - start_time
        overall_rate = processed / overall_seconds if overall_seconds > 0 else 0.0
        batch_rate = batch_len / batch_seconds if batch_seconds > 0 else 0.0
        print(
            f"[api_client] {path}: batch of {batch_len} in {batch_seconds:.2f}s ({batch_rate:.1f} rec/s) | "
            f"total processed={processed} elapsed={overall_seconds:.1f}s avg={overall_rate:.1f} rec/s",
            flush=True,
        )

    def _send_sequential(
        self,
        path: str,
        records: Iterable[dict[str, Any]],
        result_handler: Callable[[list[dict[str, Any]], list[dict[str, Any]]], None] | None,
        collect_results: bool,
    ) -> list[dict[str, Any]]:
        results: list[dict[str, Any]] = []
        batch: list[dict[str, Any]] = []
        processed = 0
        start_time = time.monotonic()
        for record in records:
            batch.append(record)
            if len(batch) == self.batch_size:
                batch_start = time.monotonic()
                batch_results = self._send_batch(path, batch, processed)
                if result_handler:
                    result_handler(batch, batch_results)
                if collect_results:
                    results.extend(batch_results)
                processed += len(batch)
                self._log_progress(path, len(batch), time.monotonic() - batch_start, processed, start_time)
                batch = []
        if batch:
            batch_start = time.monotonic()
            batch_results = self._send_batch(path, batch, processed)
            if result_handler:
                result_handler(batch, batch_results)
            if collect_results:
                results.extend(batch_results)
            processed += len(batch)
            self._log_progress(path, len(batch), time.monotonic() - batch_start, processed, start_time)
        return results

    def _send_concurrent(
        self,
        path: str,
        records: Iterable[dict[str, Any]],
        preserve_order: bool,
        result_handler: Callable[[list[dict[str, Any]], list[dict[str, Any]]], None] | None,
        collect_results: bool,
    ) -> list[dict[str, Any]]:
        """Dispatch batches to a thread pool, keeping at most ``max_workers * 2`` in flight at
        once (a bounded sliding window, not "submit everything immediately") so memory stays
        bounded for very large files (FR-004).

        When ``preserve_order`` is True, drains strictly in submission order so each result's
        index lines up with the record it belongs to — but this means one slow/unlucky batch
        blocks all progress reporting even if later batches finish first (head-of-line
        blocking). When False, drains in completion order instead, avoiding that blocking; use
        this only when the caller doesn't need positional correlation between input and result.
        """
        results: list[dict[str, Any]] = []
        processed = 0
        max_in_flight = self.max_workers * 2
        window: deque[tuple[Future, list[dict[str, Any]]]] = deque()
        pending: set[Future] = set()
        start_time = time.monotonic()

        def handle_done(future: Future, batch_records: list[dict[str, Any]]) -> None:
            nonlocal processed
            try:
                batch_results, batch_seconds = future.result()
                if result_handler:
                    result_handler(batch_records, batch_results)
                if collect_results:
                    results.extend(batch_results)
                batch_len = len(batch_records)
                processed += batch_len
                self._log_progress(path, batch_len, batch_seconds, processed, start_time)
            except ApiClientError as error:
                for pending_future in pending:
                    pending_future.cancel()
                window.clear()
                raise ApiClientError(error.message, processed) from error

        def drain_one_in_order() -> None:
            future, batch_records = window.popleft()
            pending.discard(future)
            handle_done(future, batch_records)

        def drain_one_completed() -> None:
            future = next(as_completed(pending))
            batch_records = next(records for f, records in list(window) if f is future)
            window.remove((future, batch_records))
            pending.discard(future)
            handle_done(future, batch_records)

        drain_one = drain_one_in_order if preserve_order else drain_one_completed

        executor = ThreadPoolExecutor(max_workers=self.max_workers)
        try:
            batch: list[dict[str, Any]] = []
            for record in records:
                batch.append(record)
                if len(batch) == self.batch_size:
                    future = executor.submit(self._send_batch_threadsafe, path, batch)
                    window.append((future, batch))
                    pending.add(future)
                    batch = []
                    if len(window) >= max_in_flight:
                        drain_one()
            if batch:
                future = executor.submit(self._send_batch_threadsafe, path, batch)
                window.append((future, batch))
                pending.add(future)
            while window:
                drain_one()
        finally:
            # wait=False: don't let an already-doomed batch keep the caller blocked for
            # another full cycle on top of the timeout that already fired (cancel_futures
            # only stops not-yet-started work; already-running requests finish on their own).
            executor.shutdown(wait=False, cancel_futures=True)
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
