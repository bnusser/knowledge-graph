import requests
import responses
from json import loads

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
        assert len(responses.calls) == 1
