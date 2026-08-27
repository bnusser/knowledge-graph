# java-core

Java/Spring Boot core of the Knowledge Graph project: entity/relationship CRUD and schema
modeling, backed by Neo4j. See [../specs/001-entity-crud-schema/](../specs/001-entity-crud-schema/)
for the full spec, plan, and API contract.

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+
- Docker (for Neo4j and Testcontainers)

## Run locally

```powershell
docker compose up -d
$env:APP_SECURITY_API_KEY = "local-dev-key"   # must match application.yml's app.security.api-key
mvn spring-boot:run
```

The API listens on `http://127.0.0.1:8080`. Every request to `/entities`, `/relationships`,
`/entity-types`, and `/relationship-types` requires an `X-API-Key: local-dev-key` header.

- Swagger UI: `http://127.0.0.1:8080/swagger-ui.html`
- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`

## Test

```powershell
mvn test                                       # unit tests + Testcontainers integration tests (excludes @Tag("perf"))
mvn test -Dsurefire.excludedGroups=            # includes the 1M-entity/5M-relationship performance test too
```

## Validation walkthrough

See [../specs/001-entity-crud-schema/quickstart.md](../specs/001-entity-crud-schema/quickstart.md)
for a scenario-by-scenario manual walkthrough matching the spec's acceptance criteria.
