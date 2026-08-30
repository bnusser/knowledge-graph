from kg_loaders.elliptic_loader import load

import pytest


class FakeClient:
    def __init__(self, existing_entities=None):
        self.entity_batches = []
        self.relationship_batches = []
        self.existing_entities = existing_entities or []
        self.get_calls = []

    def ensure_entity_type(self, definition):
        self.entity_definition = definition

    def ensure_relationship_type(self, definition):
        self.relationship_definition = definition

    def get(self, path):
        self.get_calls.append(path)
        return self.existing_entities

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
    assert client.entity_batches[0][0]["properties"]["dataset"] == "elliptic"
    assert client.relationship_batches[0][0]["properties"]["dataset"] == "elliptic"
    assert len(client.relationship_batches[0]) == 1
    assert summary.created == 3
    assert summary.skipped == 1
    assert "unknown transaction" in summary.skip_reasons[0]


def test_swapped_nodes_and_edges_files_fail_fast(tmp_path):
    # --nodes was accidentally pointed at edge-list-shaped content.
    nodes = tmp_path / "nodes.csv"
    nodes.write_text("txId1,txId2\ntx-a,tx-b\n", encoding="utf-8")
    classes = tmp_path / "classes.csv"
    classes.write_text("txId,class\ntx-a,licit\n", encoding="utf-8")
    edges = tmp_path / "edges.csv"
    edges.write_text("txId,class\ntx-a,licit\n", encoding="utf-8")
    client = FakeClient()

    with pytest.raises(ValueError, match="missing expected column"):
        load(nodes, edges, classes, client)


def test_headerless_features_file_is_parsed_positionally(tmp_path):
    # The authentic Elliptic features file has no header: txId, time step, ~165 features.
    nodes = tmp_path / "nodes.csv"
    nodes.write_text("tx-a,1,0.12,-0.4\ntx-b,2,0.98,1.1\n", encoding="utf-8")
    classes = tmp_path / "classes.csv"
    classes.write_text("txId,class\ntx-a,licit\ntx-b,illicit\n", encoding="utf-8")
    edges = tmp_path / "edges.csv"
    edges.write_text("txId1,txId2\ntx-a,tx-b\n", encoding="utf-8")
    client = FakeClient()

    summary = load(nodes, edges, classes, client)

    properties = client.entity_batches[0][0]["properties"]
    assert properties == {"txId": "tx-a", "timeStep": 1, "class": "licit", "dataset": "elliptic"}
    assert summary.created == 3
    assert summary.skipped == 0


def test_skip_entities_fetches_existing_ids_instead_of_resubmitting(tmp_path):
    nodes = tmp_path / "nodes.csv"
    nodes.write_text("txId,time step\ntx-a,1\ntx-b,2\n", encoding="utf-8")
    classes = tmp_path / "classes.csv"
    classes.write_text("txId,class\ntx-a,licit\ntx-b,illicit\n", encoding="utf-8")
    edges = tmp_path / "edges.csv"
    edges.write_text("txId1,txId2\ntx-a,tx-b\n", encoding="utf-8")
    client = FakeClient(existing_entities=[
        {"id": "entity-a", "properties": {"txId": "tx-a"}},
        {"id": "entity-b", "properties": {"txId": "tx-b"}},
    ])

    summary = load(nodes, edges, classes, client, skip_entities=True)

    assert client.get_calls == ["/entities?type=Transaction"]
    assert client.entity_batches == []  # never re-submitted
    assert client.relationship_batches[0][0]["sourceEntityId"] == "entity-a"
    assert client.relationship_batches[0][0]["targetEntityId"] == "entity-b"
    assert summary.created == 1  # only the relationship


def test_missing_input_is_rejected_before_schema_registration(tmp_path):
    client = FakeClient()

    with pytest.raises(FileNotFoundError):
        load(
            tmp_path / "missing-nodes.csv",
            tmp_path / "missing-edges.csv",
            tmp_path / "missing-classes.csv",
            client,
        )

    assert not hasattr(client, "entity_definition")
    assert not hasattr(client, "relationship_definition")


def test_invalid_class_header_is_rejected_before_schema_registration(tmp_path):
    nodes = tmp_path / "nodes.csv"
    nodes.write_text("tx-a,1,0.12\n", encoding="utf-8")
    classes = tmp_path / "classes.csv"
    classes.write_text("txId,label\ntx-a,licit\n", encoding="utf-8")
    edges = tmp_path / "edges.csv"
    edges.write_text("txId1,txId2\ntx-a,tx-a\n", encoding="utf-8")
    client = FakeClient()

    with pytest.raises(ValueError, match="class"):
        load(nodes, edges, classes, client)

    assert not hasattr(client, "entity_definition")
    assert not hasattr(client, "relationship_definition")

