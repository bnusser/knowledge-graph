from kg_loaders.elliptic_loader import load


class FakeClient:
    def __init__(self):
        self.entity_batches = []
        self.relationship_batches = []

    def ensure_entity_type(self, definition):
        self.entity_definition = definition

    def ensure_relationship_type(self, definition):
        self.relationship_definition = definition

    def send_entities(self, records):
        batch = list(records)
        self.entity_batches.append(batch)
        return [{"index": index, "status": "created", "id": f"entity-{index}"} for index in range(len(batch))]

    def send_relationships(self, records):
        batch = list(records)
        self.relationship_batches.append(batch)
        return [{"index": index, "status": "created", "id": f"relationship-{index}"} for index in range(len(batch))]


def test_loads_nodes_classes_and_only_resolvable_edges(tmp_path):
    nodes = tmp_path / "nodes.csv"
    nodes.write_text("txId,time step\ntx-a,1\ntx-b,2\n", encoding="utf-8")
    classes = tmp_path / "classes.csv"
    classes.write_text("txId,class\ntx-a,licit\ntx-b,illicit\n", encoding="utf-8")
    edges = tmp_path / "edges.csv"
    edges.write_text("txId1,txId2\ntx-a,tx-b\ntx-a,missing\n", encoding="utf-8")
    client = FakeClient()

    summary = load(nodes, edges, classes, client)

    assert client.entity_batches[0][0]["properties"]["class"] == "licit"
    assert len(client.relationship_batches[0]) == 1
    assert summary.created == 3
    assert summary.skipped == 1
    assert "unknown transaction" in summary.skip_reasons[0]
