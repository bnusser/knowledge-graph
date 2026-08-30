from kg_loaders.paysim_loader import load


class FakeClient:
    def __init__(self):
        self.entity_batches = []
        self.relationship_batches = []

    def ensure_entity_type(self, definition):
        self.entity_definition = definition

    def ensure_relationship_type(self, definition):
        self.relationship_definition = definition

    def send_entities(self, records, result_handler=None, collect_results=True):
        batch = list(records)
        self.entity_batches.append(batch)
        results = [{"index": index, "status": "created", "id": f"account-{index}"} for index in range(len(batch))]
        if result_handler:
            result_handler(batch, results)
        return results if collect_results else []

    def send_relationships(self, records, result_handler=None, collect_results=True):
        batch = list(records)
        self.relationship_batches.append(batch)
        results = [{"index": index, "status": "created", "id": f"transaction-{index}"} for index in range(len(batch))]
        if result_handler:
            result_handler(batch, results)
        return results if collect_results else []


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


def test_standard_paysim_csv_sample_is_compatible(tmp_path):
    source = tmp_path / "paysim.csv"
    source.write_text(
        "step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,oldbalanceDest,newbalanceDest,isFraud,isFlaggedFraud\n"
        "1,PAYMENT,9839.64,C1231006815,170136.0,160296.36,M1979787155,0.0,0.0,0,0\n"
        "1,PAYMENT,1864.28,C1666544295,21249.0,19384.72,M2044282225,0.0,0.0,0,0\n"
        "1,TRANSFER,181.0,C1305486145,181.0,0.0,C553264065,0.0,0.0,1,0\n",
        encoding="utf-8",
    )
    client = FakeClient()

    summary = load(source, client)

    accounts = client.entity_batches[0]
    relationships = client.relationship_batches[0]
    entity_dataset_property = next(
        prop for prop in client.entity_definition["properties"] if prop["name"] == "dataset"
    )
    relationship_dataset_property = next(
        prop for prop in client.relationship_definition["properties"] if prop["name"] == "dataset"
    )
    assert entity_dataset_property == {"name": "dataset", "dataType": "STRING", "required": True}
    assert relationship_dataset_property == {"name": "dataset", "dataType": "STRING", "required": True}
    assert [account["properties"]["name"] for account in accounts] == [
        "C1231006815",
        "M1979787155",
        "C1666544295",
        "M2044282225",
        "C1305486145",
        "C553264065",
    ]
    assert {account["properties"]["dataset"] for account in accounts} == {"paysim"}
    assert len(relationships) == 3
    assert {relationship["properties"]["dataset"] for relationship in relationships} == {"paysim"}
    assert relationships[0]["properties"] == {
        "step": 1,
        "transactionType": "PAYMENT",
        "amount": 9839.64,
        "oldBalanceOrig": 170136.0,
        "newBalanceOrig": 160296.36,
        "oldBalanceDest": 0.0,
        "newBalanceDest": 0.0,
        "isFraud": False,
        "isFlaggedFraud": False,
        "dataset": "paysim",
    }
    assert relationships[2]["properties"]["isFraud"] is True
    assert relationships[2]["properties"]["isFlaggedFraud"] is False
    assert summary.created == 9
    assert summary.skipped == 0


def test_invalid_input_is_rejected_before_schema_registration(tmp_path):
    source = tmp_path / "not-paysim.csv"
    source.write_text("wrong,columns\n1,2\n", encoding="utf-8")
    client = FakeClient()

    try:
        load(source, client)
        raise AssertionError("expected invalid input to fail")
    except ValueError as error:
        assert "missing expected column" in str(error)

    assert not hasattr(client, "entity_definition")
    assert not hasattr(client, "relationship_definition")
