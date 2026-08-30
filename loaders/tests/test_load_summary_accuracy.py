from pathlib import Path

from kg_loaders.elliptic_loader import load as load_elliptic
from kg_loaders.paysim_loader import load as load_paysim


class StatefulClient:
    """Small in-memory API double that preserves entity duplicate semantics across runs."""

    batch_size = 2

    def __init__(self):
        self.entities = {}
        self.next_entity_id = 0

    def ensure_entity_type(self, _definition):
        pass

    def ensure_relationship_type(self, _definition):
        pass

    def send_entities(self, records, result_handler=None, collect_results=True):
        all_results = []
        for batch in self._batches(records):
            results = []
            for index, record in enumerate(batch):
                properties = record["properties"]
                identity = properties.get("name") or properties.get("txId")
                key = (record["type"], identity)
                if key in self.entities:
                    status = "already_present"
                    entity_id = self.entities[key]
                else:
                    status = "created"
                    entity_id = f"entity-{self.next_entity_id}"
                    self.next_entity_id += 1
                    self.entities[key] = entity_id
                results.append({"index": index, "status": status, "id": entity_id})
            if result_handler:
                result_handler(batch, results)
            if collect_results:
                all_results.extend(results)
        return all_results

    def send_relationships(self, records, result_handler=None, collect_results=True):
        all_results = []
        relationship_index = 0
        for batch in self._batches(records):
            results = [
                {
                    "index": index,
                    "status": "created",
                    "id": f"relationship-{relationship_index + index}",
                }
                for index in range(len(batch))
            ]
            relationship_index += len(batch)
            if result_handler:
                result_handler(batch, results)
            if collect_results:
                all_results.extend(results)
        return all_results

    def _batches(self, records):
        batch = []
        for record in records:
            batch.append(record)
            if len(batch) == self.batch_size:
                yield batch
                batch = []
        if batch:
            yield batch


def test_paysim_summary_is_accurate_for_valid_invalid_and_rerun(tmp_path: Path):
    source = tmp_path / "paysim.csv"
    source.write_text(
        "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n"
        "1,PAYMENT,10,C1,20,10,M1,0,10,0,0\n"
        "2,TRANSFER,5,C2,5,0,C3,0,5,invalid,0\n",
        encoding="utf-8",
    )
    client = StatefulClient()

    first = load_paysim(source, client)
    second = load_paysim(source, client)

    assert (first.created, first.already_present, first.skipped) == (5, 0, 1)
    assert (second.created, second.already_present, second.skipped) == (1, 4, 1)
    assert second.already_present == 4  # every entity created by the first run


def test_elliptic_summary_is_accurate_for_valid_invalid_and_rerun(tmp_path: Path):
    nodes = tmp_path / "nodes.csv"
    nodes.write_text("txId,time step\ntx-a,1\ntx-b,2\n", encoding="utf-8")
    classes = tmp_path / "classes.csv"
    classes.write_text("txId,class\ntx-a,licit\ntx-b,illicit\n", encoding="utf-8")
    edges = tmp_path / "edges.csv"
    edges.write_text("txId1,txId2\ntx-a,tx-b\ntx-a,missing\n", encoding="utf-8")
    client = StatefulClient()

    first = load_elliptic(nodes, edges, classes, client)
    second = load_elliptic(nodes, edges, classes, client)

    assert (first.created, first.already_present, first.skipped) == (3, 0, 1)
    assert (second.created, second.already_present, second.skipped) == (1, 2, 1)
    assert second.already_present == 2  # every entity created by the first run
