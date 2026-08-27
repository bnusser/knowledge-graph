# Phase 0 Research: Entity/Relationship CRUD + Schema Modeling

## Decision: Build tool = Maven

**Rationale**: Maven is the more common build tool in enterprise Java job environments and
interview settings; picking it maximizes the interview-relevance of the code written in this
project (Constitution Principle V).

**Alternatives considered**: Gradle — equally capable and faster for incremental builds, but
rejected here purely to bias toward the more universally-expected tool for an interview
audience, not for a technical shortcoming.

## Decision: Neo4j access via Spring Data Neo4j (SDN), not the raw Java driver

**Rationale**: SDN provides repository abstractions (`Neo4jRepository`), object mapping, and
parameterized derived/`@Query` queries out of the box, which directly satisfies the
injection-prevention half of Constitution Principle IV without hand-written query-building
code, and is more idiomatic/demonstrable Spring Boot usage (Principle V).

**Alternatives considered**: Raw Neo4j Java Driver — more control, but requires hand-rolled
parameter binding and mapping; more code, more places to accidentally concatenate untrusted
input into Cypher.

## Decision: Testing stack = JUnit5 + Mockito (unit) + Testcontainers Neo4j module (integration)

**Rationale**: Satisfies Principle III (test discipline per stack) with a real, ephemeral
Neo4j instance for integration tests rather than mocking the database, catching real
Cypher/mapping issues; Testcontainers only requires Docker, which is already a dependency
for local Neo4j per the Constitution's Technology Stack.

**Alternatives considered**: An embedded/in-memory Neo4j test harness — rejected because
Neo4j does not offer a fully-compatible embedded mode for v5 that mirrors production
behavior closely enough to trust for contract-level integration tests.

## Decision: Contract format = OpenAPI 3, generated from annotated Spring controllers via springdoc

**Rationale**: Satisfies Principle II (contract-first integration); springdoc-openapi keeps the
contract in sync with the actual controller code and exports a reviewable `openapi.yaml`
consumed by later slices (MCP server, agent, frontend) without hand-maintaining a separate
spec file that can drift.

**Alternatives considered**: Hand-authored OpenAPI YAML maintained independently of code —
rejected due to higher risk of drift under a tight timeline (Principle VI).

## Decision: Network exposure for this slice = API-key authentication on every request

**Rationale**: Constitution Principle IV is NON-NEGOTIABLE about auth on network-exposed
endpoints, with API-key as the stated minimum interim bar. This slice implements a shared
`X-API-Key` header check (`ApiKeyAuthFilter`) on every `/entities`, `/relationships`,
`/entity-types`, and `/relationship-types` request, satisfying that bar directly rather than
relying on a localhost-only exception. OAuth2/JWT remains the target end-state, deferred to a
dedicated security slice per Principle VI.

**Alternatives considered**: Scoping the service to localhost-only with no authentication at
all — initially considered, but rejected because it required reinterpreting "network-exposed"
in a way the constitution doesn't actually carve out, flagged as a CRITICAL finding by
`/speckit-analyze` and superseded by adding the API-key filter instead.

## Decision: Schema/uniqueness enforcement = Neo4j uniqueness constraints + service-layer pre-validation

**Rationale**: Each entity type's designated identifying property gets a generated Neo4j
`CREATE CONSTRAINT ... IS UNIQUE` when its schema is registered, giving a hard DB-level
guarantee (FR-014), while the service layer validates against the in-memory schema definition
first to return a clear, rule-naming error (FR-013) rather than a raw database constraint
violation.

**Alternatives considered**: DB-constraint-only enforcement — rejected because raw Neo4j
constraint violation errors are not friendly enough to satisfy FR-013's requirement to
identify the specific violated rule.
