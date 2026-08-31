# Phase 1 Data Model: Graph Traversal and Analysis

This feature adds read-only request/result models and transient traversal state. It does not add
or mutate Neo4j labels, relationship types, properties, or schema constraints.

## Existing persistent records

### Entity

| Field | Type | Notes |
|---|---|---|
| `id` | string | Stable unique domain identifier; starting/source/destination lookup key |
| `type` | string | Semantic type defined by an `EntityTypeDefinition` |
| `properties` | object | Existing user-defined properties returned unchanged |

### Relationship

| Field | Type | Notes |
|---|---|---|
| `id` | string | Stable identifier used for uniqueness and deterministic ordering |
| `type` | string | Semantic type stored as a property on native `:RELATES` |
| `sourceEntityId` | string | Native relationship start entity |
| `targetEntityId` | string | Native relationship end entity |
| `properties` | object | Existing user-defined properties returned unchanged |

### RelationshipTypeDefinition

Its `name` is the authority for filter validity. A definition is valid even when no current
Relationship uses it.

## Request value objects

### TraversalDirection

Enum: `outgoing`, `incoming`, `both`.

- `outgoing`: from the current entity, follow native source to target.
- `incoming`: from the current entity, follow native target to source.
- `both`: either orientation is traversable; returned relationships retain native source/target.
- Default: `both`.

### NeighborhoodRequest

| Field | Type | Validation/default |
|---|---|---|
| `startEntityId` | string | Required; must identify an existing Entity |
| `maxHops` | integer | Optional; default 3; inclusive range 0..10 |
| `direction` | TraversalDirection | Optional; default `both` |
| `relationshipTypes` | list<string> | Optional/empty means all; otherwise distinct schema-defined names, normalized in lexical order |
| `limit` | integer | Optional; default 100; inclusive range 1..1,000 |

### ShortestPathRequest

| Field | Type | Validation/default |
|---|---|---|
| `sourceEntityId` | string | Required; must identify an existing Entity |
| `destinationEntityId` | string | Required; must identify an existing Entity |
| `maxHops` | integer | Optional; default 3; inclusive range 0..10 |
| `direction` | TraversalDirection | Optional; default `both` |
| `relationshipTypes` | list<string> | Optional/empty means all; otherwise distinct schema-defined names, normalized in lexical order |

There is deliberately no shortest-path result limit. The hop bound permits at most 11 entities
and 10 relationships in a successful response.

## Response value objects

### TraversedEntity

The public response requires the existing Entity fields `id`, `type`, and `properties`, plus:

| Field | Type | Rule |
|---|---|---|
| `distance` | integer | Minimum relationship hops from neighborhood start; start is 0 |

### TraversedRelationship

The public response requires the existing Relationship fields `id`, `type`, `sourceEntityId`,
`targetEntityId`, and `properties`, plus:

| Field | Type | Rule |
|---|---|---|
| `encounteredAtHop` | integer | Expansion hop at which the relationship first became eligible; range 1..`maxHops` |

### NeighborhoodResult

| Field | Type | Rule |
|---|---|---|
| `startEntityId` | string | Echoes the validated start |
| `maxHops` | integer | Applied normalized bound |
| `direction` | TraversalDirection | Applied normalized direction |
| `relationshipTypes` | list<string> | Applied normalized filter; empty means all |
| `limit` | integer | Applied combined-record limit |
| `resultSize` | integer | `entities.size + relationships.size`; never exceeds `limit` |
| `truncated` | boolean | True only when an eligible next expansion could not fit |
| `entities` | list<TraversedEntity> | Unique; sorted by `(distance, id)`; includes start |
| `relationships` | list<TraversedRelationship> | Unique; sorted by `(encounteredAtHop, id)` |

Invariants:

- Every returned relationship has both endpoints in `entities`.
- Every eligible relationship encountered while expanding a returned entity below `maxHops` is
  included until the deterministic combined-result prefix ends.
- A candidate expansion is atomic. Its cost is one new relationship plus each new endpoint.
- On the first candidate whose cost exceeds remaining capacity, no part is included, traversal
  stops, and `truncated=true`.
- Cycles, self-loops, cross-branch relationships, and parallel relationships appear at most once.

### ShortestPathResult

| Field | Type | Rule |
|---|---|---|
| `sourceEntityId` | string | Validated source |
| `destinationEntityId` | string | Validated destination |
| `maxHops` | integer | Applied bound |
| `direction` | TraversalDirection | Applied direction |
| `relationshipTypes` | list<string> | Applied normalized filter |
| `outcome` | `found` or `no_path` | Explicit valid-request outcome |
| `hopCount` | integer or null | Relationship count when found; null for `no_path` |
| `entities` | list<Entity> | Source-to-destination traversal order when found; empty for `no_path` |
| `relationships` | list<Relationship> | Traversal order when found; native orientation retained; empty for `no_path` |

Every Entity and Relationship in a path response requires the same identifier, type, endpoint,
and properties fields as the strict traversal contract; these fields are never optional.

Invariants:

- `found`: `entities.size = relationships.size + 1` and `hopCount = relationships.size`.
- Same endpoints produce a zero-hop `found` result with one entity.
- `no_path` is a successful HTTP outcome and never contains a partial route.
- Among equal-hop eligible routes, the ordered relationship-ID sequence is lexicographically
  smallest.

## Internal transient state

### TraversalEdge

Repository projection containing `currentEntityId`, `nextEntityId`, actual source and target
Entity records, and the Relationship. Neighborhood queries may deduplicate by relationship ID;
path queries retain a row per traversable `(current, relationship, next)` step.

### NeighborhoodState

Maps keyed by domain ID for entities, distances, relationships, and encounter hops, plus the
current frontier and remaining combined capacity. State exists only for one synchronous request.

### PathState

Visited entity IDs, current frontier, parent link per discovered entity, and the ordered
relationship-ID sequence used to rank equal-depth candidates. State exists only for one request.

## Outcome and error transitions

```text
request
  -> authentication failure                         => 401
  -> syntactically malformed query value             => 400
  -> invalid bound/direction/undefined type           => 422
  -> missing start/source/destination                 => 404
  -> valid neighborhood                               => 200 NeighborhoodResult
  -> valid path, destination discovered               => 200 outcome=found
  -> valid path, frontier exhausted or hop bound hit  => 200 outcome=no_path
```
