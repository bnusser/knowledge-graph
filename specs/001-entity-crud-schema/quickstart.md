# Quickstart: Entity/Relationship CRUD + Schema Modeling

## Prerequisites

- Docker (for Neo4j and Testcontainers)
- Java 21 (JDK)
- Maven 3.9+

## Run locally

```powershell
cd java-core
docker compose up -d        # starts Neo4j on localhost:7687 (bolt) / :7474 (browser)
$env:APP_SECURITY_API_KEY = "local-dev-key"   # must match application.yml's app.security.api-key
mvn spring-boot:run          # starts the API on localhost:8080
```

Every request to `/entities`, `/relationships`, `/entity-types`, and `/relationship-types`
MUST include an `X-API-Key: local-dev-key` header (`ApiKeyAuthFilter`) or it is rejected with
`401` (Constitution Principle IV, FR-016). OAuth2/JWT remains a later, dedicated security
slice — this is the interim minimum bar.

## Validation scenarios

These map directly to the spec's acceptance scenarios and success criteria. Every request
below includes `X-API-Key: local-dev-key` alongside the shown body/params.

1. **Define a schema, then create a conforming entity** (User Story 1 / FR-011, FR-001)
   - `POST /entity-types` with a `Person` type: properties `name` (STRING, required,
     identifying), `age` (INTEGER, optional).
   - `POST /entities` with `type: "Person"`, `properties: {"name": "Ada", "age": 30}`.
   - Expect `201` with a generated `id`.

2. **Retrieve, update, and delete that entity** (FR-002, FR-004, FR-005)
   - `GET /entities/{id}` → returns the entity.
   - `PATCH /entities/{id}` with `{"age": 31}` → `200`, subsequent `GET` reflects the change.
   - `DELETE /entities/{id}` (no relationships yet) → `204`.

3. **Reject a duplicate entity** (FR-014, SC-002)
   - Create two `Person` entities with the same `name` value.
   - Expect the second `POST /entities` to return `409`.

4. **Reject a schema violation** (FR-013, SC-002)
   - `POST /entities` with `type: "Person"` omitting `name` (required) → expect `422` naming
     the missing property.

5. **Create and traverse a relationship, including a self-relationship** (User Story 2 /
   FR-006, FR-007, Edge Cases)
   - Define a `RelationshipType` `WORKS_AT` allowing `Person` → `Organization` (or, to test the
     self-relationship clarification, a type allowing `Person` → `Person`, e.g. `MENTORS`).
   - `POST /relationships` connecting two existing entities (or one entity to itself for
     `MENTORS`).
   - `GET /entities/{sourceId}/relationships?direction=outgoing` and
     `GET /entities/{targetId}/relationships?direction=incoming` both return it.

6. **Block deletion of an entity with relationships, then cascade** (FR-005, SC-004)
   - Attempt `DELETE /entities/{id}` on an entity with an active relationship → expect `409`.
   - Retry with `DELETE /entities/{id}?cascade=true` → expect `204` and confirm the
     relationship no longer appears for the other endpoint entity.

7. **Persistence across restart** (SC-005)
   - Create entities/relationships/schema, restart the `java-core` process (Neo4j container
     stays up), and confirm all data is still retrievable.

8. **Reject requests without a valid API key** (FR-016)
   - Repeat any request above omitting the `X-API-Key` header (or with an incorrect value) →
     expect `401`.

Full request/response shapes are defined in [contracts/openapi.yaml](./contracts/openapi.yaml)
and the field-level rules in [data-model.md](./data-model.md).
