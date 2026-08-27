# Feature Specification: Entity/Relationship CRUD + Schema Modeling

**Feature Branch**: `001-entity-crud-schema`

**Created**: 2026-08-26

**Status**: Draft

**Input**: User description: "entity/relationship CRUD + schema modeling backed by Neo4j"

## Clarifications

### Session 2026-08-26

- Q: Should this first feature slice require any authentication/authorization, or is it internal-only for now with auth deferred to a later slice? → A: Internal/trusted for now; a separate later slice adds auth (API-key → OAuth2/JWT progression per constitution Principle IV). **(Superseded below — minimal API-key auth was pulled into this slice to satisfy Principle IV's non-negotiable minimum bar; see Assumptions.)**
- Q: When two clients update the same entity or relationship at nearly the same time, how should the conflict be handled? → A: Last-write-wins; the later update overwrites the earlier one, no version/conflict tracking.
- Q: Should creating a relationship where the source and target are the same entity (a self-relationship) be allowed or rejected? → A: Allowed by default; source and target may be the same entity.
- Q: How is the "identifying property" used to detect duplicate entities determined for each entity type? → A: Explicit; the schema author marks exactly one property per entity type as the identifying property when defining that type.
- Q: Is 10,000 entities the actual scale target for this project, or should the design assume a larger graph size? → A: Assume a larger graph: 1,000,000 entities / 5,000,000 relationships.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manage Graph Entities (Priority: P1)

As an API consumer, I can create, retrieve, update, and delete an entity representing a
real-world concept (e.g., a person, place, or thing), so that domain knowledge can be
stored and kept current in the graph.

**Why this priority**: Entities are the foundational unit of the knowledge graph. No other
capability (relationships, traversal, algorithms) is possible without entities existing first.

**Independent Test**: Can be fully tested by creating an entity of a known type with valid
properties, retrieving it by ID, updating one of its properties, and deleting it — verifying
each step reflects the expected state without needing relationships or schema features.

**Acceptance Scenarios**:

1. **Given** a defined entity type with property rules, **When** a consumer creates an entity
   of that type with valid properties, **Then** the entity is stored and returned with a unique
   identifier.
2. **Given** an existing entity, **When** a consumer retrieves it by identifier, **Then** the
   current properties are returned.
3. **Given** an existing entity, **When** a consumer updates one or more properties, **Then**
   the change is persisted and reflected on the next retrieval.
4. **Given** an existing entity with no relationships, **When** a consumer deletes it, **Then**
   it no longer appears in retrieval or listing operations.

---

### User Story 2 - Manage Relationships Between Entities (Priority: P2)

As an API consumer, I can create, retrieve, update, and delete a directed, typed relationship
between two existing entities, so that connections in the domain can be represented and later
queried.

**Why this priority**: Relationships are what make the graph a *graph* rather than a flat
record store; they are the second-most fundamental capability after entities exist.

**Independent Test**: Can be fully tested by creating two entities, connecting them with a
typed relationship, retrieving the relationship from either endpoint entity, updating its
properties, and deleting it — independent of schema enforcement or traversal features.

**Acceptance Scenarios**:

1. **Given** two existing entities, **When** a consumer creates a typed relationship between
   them, **Then** the relationship is stored with a direction (source → target) and is
   retrievable from either entity.
2. **Given** an existing relationship, **When** a consumer updates its properties, **Then** the
   change is persisted and reflected on the next retrieval.
3. **Given** an existing relationship, **When** a consumer deletes it, **Then** it no longer
   appears when listing relationships for either entity.
4. **Given** a request to create a relationship referencing a non-existent entity, **When**
   the consumer submits it, **Then** the system rejects it with an error identifying the
   missing entity.

---

### User Story 3 - Define and Enforce a Graph Schema (Priority: P3)

As an API consumer, I can define allowed entity types, relationship types, and their property
rules, so that data entered into the graph remains structurally consistent over time.

**Why this priority**: Schema enforcement is valuable but the graph is still usable in a
limited, unvalidated form without it — this builds on top of User Stories 1 and 2.

**Independent Test**: Can be fully tested by defining a schema for one entity type and one
relationship type, then verifying that conforming create requests succeed and
non-conforming ones (wrong property type, missing required property, disallowed
entity/relationship type) are rejected.

**Acceptance Scenarios**:

1. **Given** no prior schema, **When** a consumer defines an entity type with required and
   optional typed properties, **Then** subsequent entity creation requests are validated
   against it.
2. **Given** a defined relationship type restricted to specific source/target entity types,
   **When** a consumer attempts to create a relationship between disallowed entity types,
   **Then** the request is rejected with an error identifying the violated rule.
3. **Given** a defined entity type with a required property, **When** a consumer creates an
   entity omitting that property, **Then** the request is rejected with an error naming the
   missing property.

---

### Edge Cases

- What happens when a consumer attempts to delete an entity that still has relationships
  attached? (Blocked by default; see Assumptions for the cascade override.)
- Self-relationships are allowed: a relationship's source and target entity may be the same
  entity.
- Concurrent updates to the same entity or relationship are resolved via last-write-wins: the
  later write fully overwrites the earlier one, with no version conflict raised.
- Defining a schema element (entity or relationship type) whose name already exists is
  rejected with an error identifying the name conflict; the existing definition is left
  unchanged (consistent with schema changes being additive-only, see Assumptions).
- A create or update request naming an entity or relationship type with no matching schema
  definition is rejected with an error identifying the unknown type, rather than being
  silently accepted or auto-registering a new type.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow creating a new entity with a declared type and a set of typed
  properties.
- **FR-002**: System MUST allow retrieving a single entity by its unique identifier.
- **FR-003**: System MUST allow listing entities, optionally filtered by type.
- **FR-004**: System MUST allow updating an existing entity's properties.
- **FR-005**: System MUST allow deleting an entity, and MUST block deletion by default when
  the entity has existing relationships, returning an error identifying the blocking
  relationships, unless the caller explicitly requests cascading deletion.
- **FR-006**: System MUST allow creating a directed, typed relationship between two existing
  entities, optionally with properties.
- **FR-007**: System MUST allow retrieving relationships for a given entity, distinguishing
  incoming from outgoing relationships.
- **FR-008**: System MUST allow updating an existing relationship's properties.
- **FR-009**: System MUST allow deleting a relationship.
- **FR-010**: System MUST reject creation of a relationship that references a non-existent
  source or target entity, identifying which reference is invalid.
- **FR-011**: System MUST allow defining a schema of entity types, each with named,
  typed properties marked required or optional, and each designating exactly one property
  as that type's identifying property.
- **FR-012**: System MUST allow defining a schema of relationship types, each declaring which
  entity types may serve as its source and target, and its own typed properties.
- **FR-013**: System MUST validate every entity and relationship create/update request against
  the active schema and reject violations with an error identifying the specific rule
  violated.
- **FR-014**: System MUST prevent creation of a duplicate entity, where duplication is
  determined by matching entity type plus the value of that type's designated identifying
  property.
- **FR-015**: System MUST persist all entities, relationships, and schema definitions durably
  across application restarts.
- **FR-016**: System MUST reject any request lacking a valid API key with an authentication
  error, per the constitution's minimum interim authentication bar.
- **FR-017**: System MUST reject defining an entity type or relationship type whose name
  matches an existing definition, identifying the name conflict, leaving the existing
  definition unchanged.
- **FR-018**: System MUST reject any entity or relationship create/update request that names
  a type with no matching schema definition, identifying the unknown type.

### Key Entities

- **Entity**: Represents a single concept/node in the knowledge graph. Has a unique
  identifier, a declared type, and a set of typed properties conforming to that type's
  schema.
- **Relationship**: A directed, typed connection from one entity (source) to another
  (target). Has a declared type, an optional set of typed properties, and always references
  exactly one source and one target entity.
- **Schema Definition**: Describes the allowed entity types (with their property rules) and
  allowed relationship types (with their allowed source/target entity types and property
  rules) that all entities and relationships must conform to.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An entity can be created, retrieved, updated, and deleted in a single
  end-to-end workflow with no manual intervention.
- **SC-002**: 100% of entity or relationship create/update requests that violate the active
  schema are rejected, each with an error identifying the specific violated rule.
- **SC-003**: A relationship between two existing entities can be created and then retrieved
  from either endpoint entity in under 1 second for graphs containing up to 1,000,000 entities
  and 5,000,000 relationships.
- **SC-004**: Deleting an entity that has existing relationships never results in an
  orphaned relationship (one referencing a deleted entity) — it is either blocked or the
  relationship is removed as part of the same operation.
- **SC-005**: 100% of entities, relationships, and schema definitions present before an
  application restart are still present and unchanged after restart.

## Assumptions

- Entities are uniquely identified by a system-generated ID; duplicate detection uses the
  entity type plus the value of that type's schema-designated identifying property (marked
  explicitly by the schema author, not inferred by naming convention).
- Deleting an entity with existing relationships is blocked by default; cascading delete is
  an explicit, opt-in behavior rather than the default, to avoid silent data loss.
- Schema changes in this feature are additive only (new entity/relationship types, new
  optional properties); removing or narrowing existing schema elements is out of scope for
  this slice.
- This feature is limited to the core graph data model and CRUD/schema operations. Graph
  traversal algorithms, a query DSL, and NLP-based entity extraction are separate, later
  feature slices per the constitution's MVP-first delivery principle.
- Consumers of this capability (the MCP server, the LangGraph agent, the React front-end)
  are out of scope for this feature; this slice produces the API contract they will later
  depend on.
- Authentication in this slice is a single shared API key checked on every request (`X-API-Key`
  header); requests without a valid key are rejected. Full OAuth2/JWT remains out of scope and
  is deferred to a dedicated security slice, per the constitution's target end-state.
- Neo4j (named in the input) is the backing store for this feature; the functional
  requirements above are expressed independent of that choice so they remain verifiable
  regardless of implementation, per the storage technology decision already fixed in the
  project constitution.
