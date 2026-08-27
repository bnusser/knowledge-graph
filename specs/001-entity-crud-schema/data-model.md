# Phase 1 Data Model: Entity/Relationship CRUD + Schema Modeling

## PropertyDefinition (value object, embedded in schema types)

| Field | Type | Notes |
|---|---|---|
| name | string | Property name, unique within its owning type |
| dataType | enum: STRING, INTEGER, FLOAT, BOOLEAN, DATE | Used to validate property values on create/update |
| required | boolean | If true, omitting this property on create is rejected (FR-013) |

## EntityTypeDefinition (schema)

| Field | Type | Notes |
|---|---|---|
| name | string | Unique entity type name (e.g., `Person`, `Organization`) |
| properties | list\<PropertyDefinition\> | Allowed/required properties for entities of this type |
| identifyingProperty | string | Must reference exactly one `properties[].name`; used for duplicate detection (FR-011, FR-014) |

**Validation rules**:
- `identifyingProperty` MUST match one of `properties[].name`.
- Redefining an existing `name` is an additive-only operation in this slice: new optional
  properties may be added; removing/narrowing existing properties is unsupported (per spec
  Assumptions).

## RelationshipTypeDefinition (schema)

| Field | Type | Notes |
|---|---|---|
| name | string | Unique relationship type name (e.g., `WORKS_AT`) |
| allowedSourceTypes | list\<string\> | EntityTypeDefinition names permitted as source |
| allowedTargetTypes | list\<string\> | EntityTypeDefinition names permitted as target (may overlap with source, enabling self-relationships per spec clarification) |
| properties | list\<PropertyDefinition\> | Allowed/required properties for relationships of this type |

## Entity

| Field | Type | Notes |
|---|---|---|
| id | string (UUID) | System-generated, immutable |
| type | string | Must reference an existing `EntityTypeDefinition.name` (FR-001) |
| properties | map\<string, any\> | Validated against `type`'s `EntityTypeDefinition.properties` on every create/update (FR-013) |

**Validation rules**:
- `type` MUST exist in the schema (FR-001, FR-013).
- All `required` properties for `type` MUST be present (FR-013).
- Property values MUST match their declared `dataType` (FR-013).
- No two entities of the same `type` may share the same value for the `identifyingProperty`
  (FR-014).
- Deletion is blocked while any `Relationship` references this entity as source or target,
  unless the caller explicitly requests cascade (FR-005).

## Relationship

| Field | Type | Notes |
|---|---|---|
| id | string (UUID) | System-generated, immutable |
| type | string | Must reference an existing `RelationshipTypeDefinition.name` (FR-006) |
| sourceEntityId | string | Must reference an existing `Entity.id`; type must be in `allowedSourceTypes` (FR-010) |
| targetEntityId | string | Must reference an existing `Entity.id`; type must be in `allowedTargetTypes` (FR-010); may equal `sourceEntityId` (self-relationships allowed per clarification) |
| properties | map\<string, any\> | Validated against `type`'s `RelationshipTypeDefinition.properties` |

**Concurrency note**: Updates to `Entity` or `Relationship` use last-write-wins (per
clarification) — no version/optimistic-locking field is modeled.

## State / Lifecycle

Entities and relationships have no explicit state machine — they exist from creation until
deletion. Schema definitions (`EntityTypeDefinition`, `RelationshipTypeDefinition`) are
additive-only for this slice: once created, a type is not removed or narrowed, only extended
with new optional properties.
