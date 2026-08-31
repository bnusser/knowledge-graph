# Feature Specification: Graph Traversal and Analysis

**Feature Branch**: `003-graph-traversal-analysis`

**Created**: 2026-08-30

**Status**: Draft

**Input**: User description: "Add graph traversal and analysis capabilities to the Java core, including bounded multi-hop neighborhood traversal, shortest path, configurable direction and relationship-type filters, cycle-safe execution, result limits, and tests against representative graph structures."

## Clarifications

### Session 2026-08-30

- Q: Should a supplied relationship type be considered valid when it exists in the graph schema but currently has no relationship instances? → A: Valid if defined in the graph schema.
- Q: When the next breadth-first expansion cannot fit both its relationship and newly reached endpoint within the result limit, should traversal stop or skip that expansion and consider later candidates? → A: Stop and mark the result truncated.
- Q: Should a neighborhood include every eligible relationship encountered within the hop bound, including cycle-closing, cross-branch, and parallel relationships, or only relationships that first discover an entity? → A: Include all eligible encountered relationships.
- Q: Should shortest-path requests have a separate caller-supplied result limit in addition to the maximum hop count? → A: No separate path result limit.
- Q: Which loaded datasets must satisfy the two-second traversal performance criterion? → A: Full PaySim and Elliptic datasets.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Explore a Bounded Neighborhood (Priority: P1) — MVP

As a developer or downstream graph consumer, I can explore the entities and relationships
within a bounded number of hops from a selected entity so I can understand its local context
without retrieving the entire graph.

**Why this priority**: Neighborhood exploration is the smallest demoable traversal slice and
directly supports investigation, visualization, and later agent-driven graph questions.

**Independent Test**: Create a graph containing branches, cycles, and disconnected entities,
request a neighborhood from one entity with a specified hop bound and result limit, and
verify that only reachable records within both bounds are returned once.

**Acceptance Scenarios**:

1. **Given** a connected graph and a valid starting entity, **When** a consumer requests its
   neighborhood through two hops, **Then** the result contains the starting entity and every
   reachable entity and relationship within two hops, subject to the result limit.
2. **Given** a graph containing a cycle, **When** a consumer explores beyond the cycle length,
   **Then** the operation completes without repeatedly expanding the same records.
3. **Given** a starting entity with no matching relationships, **When** its neighborhood is
   requested, **Then** the result contains the starting entity and no relationships.

---

### User Story 2 - Find a Shortest Path (Priority: P2)

As a developer or downstream graph consumer, I can find one shortest unweighted path between
two entities within a maximum hop count so I can explain how they are connected.

**Why this priority**: A shortest path is the first required graph-analysis capability after
the traversal MVP and provides a concise, explainable connection between entities.

**Independent Test**: Create a graph with multiple routes of different lengths, request a
bounded path between two entities, and verify that the returned route uses the fewest hops
among routes allowed by the request.

**Acceptance Scenarios**:

1. **Given** two connected entities with routes of different lengths, **When** a shortest
   path is requested with a sufficient hop bound, **Then** one route with the minimum number
   of relationships is returned in traversal order.
2. **Given** two entities with no allowed route within the hop bound, **When** a shortest path
   is requested, **Then** the result clearly reports that no path was found.
3. **Given** the same entity as both endpoints, **When** a shortest path is requested, **Then**
   a zero-hop path containing that entity is returned.

---

### User Story 3 - Constrain and Predict Traversal Results (Priority: P3)

As a graph consumer, I can choose traversal direction, restrict traversal to selected
relationship types, and cap returned results so the answer matches my question and remains
safe to use on a large graph.

**Why this priority**: Filters and hard bounds turn basic traversal into a predictable
capability suitable for the full loaded datasets and future user-facing consumers.

**Independent Test**: Use a graph with incoming and outgoing relationships of several types,
repeat the same traversal with different directions, type filters, and limits, and verify
that every result obeys the requested constraints and reports truncation when applicable.

**Acceptance Scenarios**:

1. **Given** mixed incoming and outgoing relationships, **When** a consumer chooses one
   direction, **Then** expansion follows only relationships allowed by that direction.
2. **Given** relationships of several semantic types, **When** a consumer supplies a type
   filter, **Then** only relationships whose type is in the filter contribute to the result.
3. **Given** a reachable neighborhood larger than the requested result limit, **When** the
   traversal runs, **Then** the combined count of unique returned entities and relationships
   does not exceed the limit and the result explicitly indicates that it was truncated.

### Edge Cases

- A starting or destination entity does not exist.
- A hop bound is zero, negative, or greater than the supported maximum.
- A result limit is zero, negative, or greater than the supported maximum.
- A relationship-type filter is empty or names a type that does not exist.
- The graph contains self-loops, parallel relationships, dense hubs, or cycles.
- A path exists in the graph but is excluded by direction, relationship-type, or hop filters.
- Several equally short paths exist between the requested endpoints.
- The result limit is reached partway through expanding a traversal frontier.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow a consumer to request a neighborhood beginning at one
  existing entity and bounded by a maximum number of relationship hops. The default is three
  hops when omitted, and requests above ten hops are invalid.
- **FR-002**: A neighborhood result MUST identify the starting entity, all returned entities,
  all returned relationships, and each returned entity's minimum hop distance from the start.
- **FR-003**: The system MUST support traversal directions of outgoing, incoming, and both,
  using both as the default when direction is omitted.
- **FR-004**: The system MUST allow traversal to be restricted to one or more relationship
  types defined in the graph schema; when no type filter is supplied, all relationship types
  are eligible. A supplied type not defined in the schema makes the request invalid, while a
  defined type remains valid when it currently has no relationship instances.
- **FR-005**: The system MUST enforce a caller-supplied result limit and a system-defined hard
  maximum for every neighborhood request. The limit counts the combined unique entities
  (including the start) and relationships returned; it defaults to 100 and cannot exceed
  1,000.
- **FR-006**: A limited neighborhood result MUST state whether it was truncated so consumers
  can distinguish a complete neighborhood from a bounded partial result. A relationship may
  be returned only when both endpoint entities are also returned; an expansion that cannot
  fit the relationship and any newly reached endpoint within the remaining limit ends the
  traversal immediately, omits that expansion, and marks the result as truncated. Later
  candidates MUST NOT be considered after the first expansion that cannot fit.
- **FR-007**: Traversal MUST be cycle-safe and MUST NOT return the same entity or relationship
  more than once in a neighborhood result. It MUST return every eligible relationship
  encountered while expanding an entity whose minimum distance is less than the hop bound,
  including cycle-closing, cross-branch, and parallel relationships, when both endpoints fit
  within the result limit. Relationships that first discover an entity and relationships
  whose endpoints were already discovered are subject to the same inclusion rule.
- **FR-008**: The system MUST allow a consumer to request one shortest unweighted path between
  two existing entities, bounded by a maximum number of hops, using the same default of three
  and hard maximum of ten. Shortest-path requests MUST NOT have a separate result limit; the
  operation returns a complete eligible path within the hop bound or a no-path outcome and
  MUST NOT return a partial path.
- **FR-009**: A shortest-path result MUST preserve traversal order and include the ordered
  entities and relationships needed to explain the route.
- **FR-010**: When multiple equally short paths exist, the system MUST choose one using a
  stable tie-breaking rule: compare the ordered relationship-identifier sequences and return
  the lexicographically smallest sequence.
- **FR-011**: Neighborhood records MUST be selected and returned in a stable breadth-first
  order. Entities are ordered by minimum hop distance then entity identifier; relationships
  are ordered by the hop on which they are encountered then relationship identifier.
- **FR-012**: Direction and relationship-type filters MUST apply consistently to both
  neighborhood traversal and shortest-path requests.
- **FR-013**: The system MUST clearly distinguish a missing endpoint, an invalid request, and
  a valid request for which no path or matching neighborhood expansion exists.
- **FR-014**: Traversal and path operations MUST be read-only and MUST NOT modify graph data or
  schema definitions.
- **FR-015**: Traversal inputs MUST be validated before graph execution and MUST use the
  project's existing access-control requirements.
- **FR-016**: The feature MUST include automated examples covering linear paths, branching
  graphs, cycles, self-loops, disconnected components, mixed directions, multiple
  relationship types, equal shortest paths, and result truncation.
- **FR-017**: Each completed operation MUST provide enough diagnostic context to identify the
  operation category, applied bounds, result size, truncation state, and outcome without
  exposing credentials or unrelated graph properties.

### Key Entities

- **Traversal Request**: The starting entity, maximum hop count, direction, optional eligible
  relationship types, and result limit that define a neighborhood exploration.
- **Neighborhood Result**: The unique entities and relationships reached by a traversal,
  minimum distances from the start, and whether the result was truncated.
- **Shortest Path Request**: The source entity, destination entity, maximum hop count,
  direction, and optional eligible relationship types.
- **Path Result**: An ordered sequence of entities and relationships representing one
  shortest eligible route, or an explicit no-path outcome.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For every representative graph scenario, neighborhood results contain 100% of
  records allowed by the requested hop, direction, and type constraints until the explicit
  result limit is reached, with no duplicates.
- **SC-002**: For every representative graph with a reachable route, the returned path has the
  minimum eligible hop count and can be followed from source to destination without missing
  or out-of-order records.
- **SC-003**: Cyclic, self-referential, and disconnected graph scenarios complete without
  unbounded expansion, repeated records, or graph mutations.
- **SC-004**: Every neighborhood contains no more combined unique entities and relationships
  than the requested result limit, and every truncated neighborhood is explicitly identified
  as truncated.
- **SC-005**: At least 95% of bounded requests with at most three hops and a result limit of
  1,000 complete within two seconds when validated separately against the project's fully
  loaded PaySim dataset and fully loaded Elliptic dataset.
- **SC-006**: Invalid bounds, missing entities, and no-path outcomes are distinguishable in
  100% of acceptance tests without requiring consumers to inspect logs.
- **SC-007**: Repeating the same request against an unchanged graph produces the same ordered
  result in 100% of deterministic-result tests.

## Assumptions

- The neighborhood traversal in User Story 1 is the minimal demoable slice. Shortest path is
  the required follow-on analysis slice; weighted paths, centrality, community detection,
  similarity, and other advanced algorithms are stretch goals outside this feature.
- Shortest path means fewest relationships, not a weighted cost calculation.
- Hop bounds default to three and have a hard maximum of ten. Neighborhood result limits
  default to 100 combined records and have a hard maximum of 1,000.
- An empty relationship-type filter has the same meaning as no filter: all relationship
  types are eligible.
- The neighborhood result limit counts the combined unique entities (including the starting
  entity) and relationships returned.
- The existing graph data model, entity and relationship identifiers, authentication rules,
  and local runtime remain authoritative dependencies.
- Traversal and analysis are synchronous, read-only operations in this feature. Background
  jobs, saved traversal queries, streaming results, and distributed execution are out of
  scope.
