package com.knowledgegraph.core.schema;

import java.util.Map;

/**
 * Foundational-phase placeholder: accepts everything, performing no validation. Kept for
 * reference/tests; {@link SchemaValidatorImpl} (US3) is the active {@code @Component} that
 * actually enforces schema rules — see {@code SchemaValidatorConfig}.
 */
public class NoOpSchemaValidator implements SchemaValidator {

    @Override
    public void validateEntityTypeDefinition(EntityTypeDefinition definition) {
        // no-op
    }

    @Override
    public void validateEntityProperties(EntityTypeDefinition definition, Map<String, Object> properties) {
        // no-op
    }

    @Override
    public void validateRelationship(
            RelationshipTypeDefinition definition,
            String sourceEntityType,
            String targetEntityType,
            Map<String, Object> properties) {
        // no-op
    }
}
