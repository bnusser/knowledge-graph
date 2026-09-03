# Knowledge Graph

A knowledge-graph platform built with a spec-driven development process: a Java/Spring Boot
graph core (source of truth) backed by Neo4j, an MCP server exposing that API as tools, a
Python LangGraph agent that orchestrates those tools, and a React front-end — each built as an
independent, incrementally-delivered slice.

See [.specify/memory/constitution.md](.specify/memory/constitution.md) for the project's
governing principles and full technology stack, and [specs/](specs/) for every feature's
spec/plan/tasks.

## Architecture

```mermaid
flowchart LR
    UI[React Front-End] --> Agent[Python LangGraph Agent]
    Agent --> MCP[MCP Server]
    MCP --> Core[Java / Spring Boot Core]
    Core --> DB[(Neo4j)]
```

| Layer | Status | Location |
|---|---|---|
| Java core (CRUD, schema, traversal/analysis, Neo4j) | ✅ Implemented | [java-core/](java-core/) |
| Dataset loaders (Elliptic + PaySim, bulk-import API) | ✅ Implemented | [loaders/](loaders/) |
| MCP server (exposes the Java API as MCP tools) | Not started | future slice |
| Python LangGraph agent (agentic tool-use orchestrator) | Not started | future slice |
| React front-end (visualization, search, chat, admin CRUD) | Not started | future slice |

Only the Java core and dataset loaders are built so far; the remaining layers are separate,
later feature slices per the constitution's MVP-first delivery principle (see Development
Workflow below).

## Roadmap

The constitution's Principle VI ("North-Star Scope with MVP-First Delivery") defines the full
target system; this is the planned order of feature slices toward it. Each is specified,
planned, and tasked via Spec Kit (see Development Workflow below) only when work on it begins —
entries below aren't yet-created specs, just the intended sequence.

1. ✅ `001-entity-crud-schema` — Java/Spring Boot core: entity/relationship CRUD, schema modeling
2. ✅ `002-elliptic-paysim-loaders` — bulk-import API + Elliptic/PaySim dataset loaders
3. ✅ `003-graph-traversal-analysis` — bounded neighborhoods + deterministic shortest paths
4. Query DSL
5. NLP-based entity extraction
6. MCP server (exposes the Java API as MCP tools)
7. Python LangGraph agent (agentic tool-use orchestrator)
8. React front-end (visualization, search, chat, admin CRUD)
9. Full OAuth2/JWT auth (upgrade from the interim API-key bar)

This order isn't fixed — re-prioritize freely as the project evolves, updating this list in the
same change.

## Repository Structure

```text
.specify/            Spec Kit tooling, memory (constitution), templates
specs/                Per-feature spec.md / plan.md / tasks.md / research.md / contracts/
java-core/            Java/Spring Boot graph core (implemented)
.github/skills/       Spec Kit slash-command definitions
```

## Prerequisites

For the implemented layer (`java-core/`):

- Java 21 (JDK)
- Maven 3.9+
- Docker (for Neo4j locally and for Testcontainers-based integration tests)

For the dataset loaders (`loaders/`): Python 3.12+ with `uv`.

Future layers will additionally need Python 3.12+ (with `uv`) for the agent, and Node.js/React
for the front-end — not required yet.

## Build & Run

```powershell
cd java-core
docker compose up -d
$env:APP_SECURITY_API_KEY = "local-dev-key"   # must match application.yml's app.security.api-key
mvn spring-boot:run
```

The API listens on `http://127.0.0.1:8080`. Every request to `/entities`, `/relationships`,
`/entity-types`, and `/relationship-types` requires an `X-API-Key: local-dev-key` header
(Constitution Principle IV's minimum interim auth bar — OAuth2/JWT is a later, dedicated
security slice).

- Swagger UI: `http://127.0.0.1:8080/swagger-ui.html`
- OpenAPI JSON: `http://127.0.0.1:8080/v3/api-docs`

Full details, including a scenario-by-scenario validation walkthrough, are in
[java-core/README.md](java-core/README.md) and
[specs/001-entity-crud-schema/quickstart.md](specs/001-entity-crud-schema/quickstart.md).

## Graph Traversal and Analysis

The Java core supports bounded, cycle-safe multi-hop neighborhoods and deterministic shortest
paths. Both operations accept `maxHops`, `direction` (`outgoing`, `incoming`, or `both`), and
repeated schema-defined `relationshipTypes`; neighborhoods additionally enforce a combined
entity-plus-relationship `limit` and report truncation.

```powershell
$headers = @{ "X-API-Key" = "local-dev-key" }
Invoke-RestMethod -Headers $headers `
  -Uri "http://127.0.0.1:8080/entities/<id>/neighborhood?maxHops=2&direction=both&limit=100"
Invoke-RestMethod -Headers $headers `
  -Uri "http://127.0.0.1:8080/entities/<source>/shortest-path/<destination>?maxHops=3&direction=outgoing"
```

Design and validation details: [spec](specs/003-graph-traversal-analysis/spec.md),
[plan](specs/003-graph-traversal-analysis/plan.md),
[API contract](specs/003-graph-traversal-analysis/contracts/traversal-api.yaml), and
[quickstart](specs/003-graph-traversal-analysis/quickstart.md).

## Dataset Loaders

`loaders/` contains Python CLI loaders that populate the graph via `java-core`'s bulk-import
API. With `java-core` and Neo4j already running:

```powershell
cd loaders
uv sync
uv run python -m kg_loaders.elliptic_loader `
  --nodes path\to\elliptic_txs_features.csv `
  --edges path\to\elliptic_txs_edgelist.csv `
  --classes path\to\elliptic_txs_classes.csv `
  --api-url http://127.0.0.1:8080 `
  --api-key local-dev-key `
  --max-workers 8 `
  --timeout 60  
```

To load the included PaySim dataset, run the following from `loaders/`:

```powershell
uv run python -m kg_loaders.paysim_loader `
  --input ..\data\paysim1\PS_20174392719_1491204439457_log.csv `
  --api-url http://127.0.0.1:8080 `
  --api-key local-dev-key `
  --max-workers 8 `
  --timeout 60
```

Add `--limit N` to load only the first `N` rows, which is useful for a quick validation run.
Full details in [specs/002-elliptic-paysim-loaders/quickstart.md](specs/002-elliptic-paysim-loaders/quickstart.md).

## Test

```powershell
cd java-core
mvn verify                                     # unit tests + Testcontainers integration tests (excludes @Tag("perf"))
mvn verify "-Dsurefire.excludedGroups=" "-Dtest=*Test" "-Dit.test=RelationshipTraversalPerformanceIT"  # legacy synthetic perf regression
```

### Windows + Docker Desktop troubleshooting

If Testcontainers-based integration tests fail with `Could not find a valid Docker environment`
or a `BadRequestException` full of empty/zero fields, Docker Desktop's default Windows named-pipe
transport may not be negotiating correctly with the bundled `docker-java` client's default API
version. Workaround used during initial development:

1. Docker Desktop → Settings → General → enable **"Expose daemon on tcp://localhost:2375 without
   TLS"** and restart Docker Desktop. (Trade-off: any local process can then control Docker
   unauthenticated. Fine for a personal dev machine; don't do this on a shared machine.)
2. Run tests with:

   ```powershell
   $env:DOCKER_HOST = "tcp://127.0.0.1:2375"
   mvn verify "-Dapi.version=1.45"
   ```

## Development Workflow

This project uses [Spec Kit](https://github.com/github/spec-kit) for spec-driven development.
Each architectural layer is its own feature slice:

1. `/speckit-specify` — describe the feature (what/why, not how)
2. `/speckit-clarify` — resolve ambiguities before planning (required when a slice touches a
   cross-language contract or security boundary)
3. `/speckit-plan` — technical plan, constitution compliance check, contracts, data model
4. `/speckit-tasks` — dependency-ordered task breakdown
5. `/speckit-analyze` — cross-artifact consistency check before implementing
6. `/speckit-implement` — build it
7. `/speckit-converge` — catch up tasks.md with any drift before moving on

See [specs/001-entity-crud-schema/](specs/001-entity-crud-schema/) for a complete worked example
of every artifact this process produces.
