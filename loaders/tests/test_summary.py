from kg_loaders.summary import LoadSummary


def test_summary_tracks_outcomes_and_reasons():
    summary = LoadSummary()
    summary.add_results([
        {"status": "created"},
        {"status": "already_present"},
        {"status": "rejected", "error": "unknown type"},
    ])
    summary.skip("malformed row")

    assert summary.created == 1
    assert summary.already_present == 1
    assert summary.skipped == 2
    assert summary.skip_reasons == ["unknown type", "malformed row"]
    assert "created=1" in summary.format()
