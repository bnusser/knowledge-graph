from kg_loaders.paysim_loader import load


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
        return [{"index": index, "status": "created", "id": f"account-{index}"} for index in range(len(batch))]

    def send_relationships(self, records):
        batch = list(records)
        self.relationship_batches.append(batch)
        return [{"index": index, "status": "created", "id": f"transaction-{index}"} for index in range(len(batch))]


def test_limit_is_deterministic_and_values_are_coerced(tmp_path):
    source = tmp_path / "paysim.csv"
    source.write_text(
        "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n"
        "1,PAYMENT,10.5,C1,20,9.5,M1,0,10.5,1,0\n"
        "2,TRANSFER,3,C2,4,1,M2,8,11,0,1\n"
        "3,CASH_OUT,2,C3,2,0,M3,0,2,not-bool,0\n",
        encoding="utf-8",
    )
    client = FakeClient()

    summary = load(source, client, limit=2)

    assert len(client.entity_batches[0]) == 4
    assert len(client.relationship_batches[0]) == 2
    properties = client.relationship_batches[0][0]["properties"]
    assert properties["amount"] == 10.5
    assert properties["isFraud"] is True
    assert properties["isFlaggedFraud"] is False
    assert properties["dataset"] == "paysim"
    assert client.entity_batches[0][0]["properties"]["dataset"] == "paysim"
    assert summary.skipped == 0


def test_malformed_row_is_skipped(tmp_path):
    source = tmp_path / "paysim.csv"
    source.write_text(
        "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n"
        "bad,PAYMENT,10,C1,20,9,M1,0,10,0,0\n",
        encoding="utf-8",
    )
    client = FakeClient()

    summary = load(source, client)

    assert summary.skipped == 1
    assert "malformed PaySim row" in summary.skip_reasons[0]
