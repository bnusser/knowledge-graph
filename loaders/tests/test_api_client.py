import time

import requests
import responses
from json import dumps, loads

from kg_loaders.api_client import ApiClient, ApiClientError


@responses.activate
def test_entities_are_batched_and_authenticated():
    responses.add(responses.POST, "http://core/entities/bulk", json={"results": []}, status=200)
    responses.add(responses.POST, "http://core/entities/bulk", json={"results": []}, status=200)
    client = ApiClient("http://core", "secret", batch_size=2)

    results = client.send_entities([{"type": "Account"}] * 3)

    assert results == []
    assert len(responses.calls) == 2
    assert all(call.request.headers["X-API-Key"] == "secret" for call in responses.calls)
    assert [len(loads(call.request.body)["items"]) for call in responses.calls] == [2, 1]


@responses.activate
def test_connection_failure_reports_records_processed():
    responses.add(responses.POST, "http://core/entities/bulk", json={"results": []}, status=200)
    responses.add(responses.POST, "http://core/entities/bulk", body=requests.ConnectionError("offline"))
    client = ApiClient("http://core", "secret", batch_size=2)

    try:
        client.send_entities([{"type": "Account"}] * 3)
        raise AssertionError("expected ApiClientError")
    except ApiClientError as error:
        assert error.processed_count == 2
        assert "offline" in str(error)


@responses.activate
def test_http_failure_stops_before_later_batches():
    responses.add(responses.POST, "http://core/relationships/bulk", json={}, status=503)
    client = ApiClient("http://core", "secret", batch_size=2)

    try:
        client.send_relationships([{"type": "TRANSACTION"}] * 4)
        raise AssertionError("expected ApiClientError")
    except ApiClientError as error:
        assert error.processed_count == 0


@responses.activate
def test_concurrent_dispatch_preserves_submission_order():
    def callback(request):
        items = loads(request.body)["items"]
        results = [{"index": i, "status": "created", "id": item["marker"]} for i, item in enumerate(items)]
        return (200, {}, dumps({"results": results}))

    responses.add_callback(
        responses.POST, "http://core/entities/bulk", callback=callback, content_type="application/json"
    )
    client = ApiClient("http://core", "secret", batch_size=2, max_workers=3)
    records = [{"type": "Account", "marker": f"m{i}"} for i in range(10)]

    results = client.send_entities(records)

    assert [r["id"] for r in results] == [f"m{i}" for i in range(10)]


@responses.activate
def test_concurrent_dispatch_stops_and_reports_processed_before_failure():
    def callback(request):
        items = loads(request.body)["items"]
        if any(item.get("marker") == "fail-me" for item in items):
            return (503, {}, "boom")
        return (200, {}, dumps({"results": [{"index": i, "status": "created"} for i in range(len(items))]}))

    responses.add_callback(
        responses.POST, "http://core/entities/bulk", callback=callback, content_type="application/json"
    )
    client = ApiClient("http://core", "secret", batch_size=1, max_workers=2)
    records = (
        [{"type": "Account", "marker": f"ok-{i}"} for i in range(3)]
        + [{"type": "Account", "marker": "fail-me"}]
        + [{"type": "Account", "marker": f"ok-{i}"} for i in range(3, 6)]
    )

    try:
        client.send_entities(records)
        raise AssertionError("expected ApiClientError")
    except ApiClientError as error:
        # Batches drain strictly in submission order regardless of completion order, so the 3
        # batches before the failing one are guaranteed counted, and no more. Cancellation of
        # not-yet-started futures is best-effort — already-dispatched concurrent requests may
        # still complete server-side even though they aren't reflected in processed_count.
        assert error.processed_count == 3


@responses.activate
def test_relationships_drain_in_completion_order_not_blocked_by_slow_first_batch():
    # Batch 0 (first submitted) is deliberately the slowest; batches 1 and 2 finish fast.
    # send_relationships must not be head-of-line-blocked waiting on batch 0 specifically —
    # all results must still come back correctly once everything drains.
    def callback(request):
        items = loads(request.body)["items"]
        marker = items[0]["marker"]
        if marker == "slow":
            time.sleep(0.3)
        return (200, {}, dumps({"results": [{"index": 0, "status": "created", "id": marker}]}))

    responses.add_callback(
        responses.POST, "http://core/relationships/bulk", callback=callback, content_type="application/json"
    )
    client = ApiClient("http://core", "secret", batch_size=1, max_workers=3)
    records = [{"type": "TRANSACTION", "marker": "slow"}, {"type": "TRANSACTION", "marker": "fast-1"},
               {"type": "TRANSACTION", "marker": "fast-2"}]

    start = time.monotonic()
    results = client.send_relationships(records)
    elapsed = time.monotonic() - start

    assert {r["id"] for r in results} == {"slow", "fast-1", "fast-2"}
    assert elapsed < 0.6  # well under 2x the slow batch's own delay; no compounding wait
