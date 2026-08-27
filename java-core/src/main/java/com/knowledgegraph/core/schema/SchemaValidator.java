package com.knowledgegraph.core.schema;

import com.knowledgegraph.core.exception.SchemaValidationException;
import java.util.Map;

/**
 * Validates entity/relationship type definitions and entity/relationship data against the
 * active schema (FR-013). Introduced in the Foundational phase as an interface with a no-op
 * default ({@link NoOpSchemaValidator}) so US1/US2 can call it from day one; {@link SchemaValidatorImpl}
 * supplies the real enforcement (US3).
 */
public interface SchemaValidator {

    /** Validates a newly-defined entity type's own rules (e.g. identifyingProperty references a declared property). */
    void validateEntityTypeDefinition(EntityTypeDefinition definition) throws SchemaValidationException;

    /** Validates entity property values against its type's schema on create/update. */
    void validateEntityProperties(EntityTypeDefinition definition, Map<String, Object> properties)
            throws SchemaValidationException;

    /** Validates a relationship's source/target entity types and property values against its type's schema. */
    void validateRelationship(
            RelationshipTypeDefinition definition,
            String sourceEntityType,
            String targetEntityType,
            Map<String, Object> properties)
            throws SchemaValidationException;
}
