import math
import os
import tracemalloc
from pathlib import Path

import pytest

from kg_loaders.paysim_loader import load


class StreamingScaleClient:
    def __init__(self, batch_size=500):
        self.batch_size = batch_size
        self.entity_requests = 0
        self.relationship_requests = 0
        self.next_id = 0

    def ensure_entity_type(self, _definition):
        pass

    def ensure_relationship_type(self, _definition):
        pass

    def send_entities(self, records, result_handler=None, collect_results=True):
        return self._consume(records, result_handler, collect_results, entities=True)

    def send_relationships(self, records, result_handler=None, collect_results=True):
        return self._consume(records, result_handler, collect_results, entities=False)

    def _consume(self, records, result_handler, collect_results, entities):
        collected = []
        batch = []
        for record in records:
            batch.append(record)
            if len(batch) == self.batch_size:
                self._complete(batch, result_handler, collected, collect_results, entities)
                batch = []
        if batch:
            self._complete(batch, result_handler, collected, collect_results, entities)
        return collected

    def _complete(self, batch, result_handler, collected, collect_results, entities):
        if entities:
            self.entity_requests += 1
        else:
            self.relationship_requests += 1
        results = []
        for index in range(len(batch)):
            results.append({"index": index, "status": "created", "id": f"id-{self.next_id}"})
            self.next_id += 1
        if result_handler:
            result_handler(batch, results)
        if collect_results:
            collected.extend(results)


@pytest.mark.slow
def test_full_scale_load_has_bounded_memory_and_expected_request_count(tmp_path: Path):
    row_count = int(os.environ.get("PAYSIM_SCALE_ROWS", "6362620"))
    source = tmp_path / "paysim-scale.csv"
    with source.open("w", encoding="utf-8", newline="") as output:
        output.write(
            "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n"
        )
        for index in range(row_count):
            output.write(f"1,TRANSFER,1.0,C{index},1.0,0.0,M{index},0.0,1.0,0,0\n")

    client = StreamingScaleClient(batch_size=500)
    tracemalloc.start()
    summary = load(source, client)
    _current, peak = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    assert peak < 256 * 1024 * 1024
    assert client.entity_requests == math.ceil((row_count * 2) / client.batch_size)
    assert client.relationship_requests == math.ceil(row_count / client.batch_size)
    assert summary.created == row_count * 3
