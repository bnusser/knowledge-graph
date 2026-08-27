package com.knowledgegraph.core.schema;

import com.knowledgegraph.core.exception.SchemaValidationException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Real schema enforcement (FR-013): required/typed properties, identifying-property references,
 * and relationship source/target type restrictions. Replaces {@link NoOpSchemaValidator} as the
 * active bean (see {@code SchemaValidatorConfig}).
 */
@Component
public class SchemaValidatorImpl implements SchemaValidator {

    @Override
    public void validateEntityTypeDefinition(EntityTypeDefinition definition) {
        boolean identifyingPropertyDeclared = definition.getProperties().stream()
                .anyMatch(property -> property.name().equals(definition.getIdentifyingProperty()));
        if (!identifyingPropertyDeclared) {
            throw new SchemaValidationException("identifyingProperty '" + definition.getIdentifyingProperty()
                    + "' must reference a declared property of entity type '" + definition.getName() + "'");
        }
    }

    @Override
    public void validateEntityProperties(EntityTypeDefinition definition, Map<String, Object> properties) {
        validateProperties(definition.getProperties(), properties, "entity type '" + definition.getName() + "'");
    }

    @Override
    public void validateRelationship(
            RelationshipTypeDefinition definition,
            String sourceEntityType,
            String targetEntityType,
            Map<String, Object> properties) {
        if (!definition.getAllowedSourceTypes().contains(sourceEntityType)) {
            throw new SchemaValidationException("relationship type '" + definition.getName()
                    + "' does not allow source entity type '" + sourceEntityType + "'");
        }
        if (!definition.getAllowedTargetTypes().contains(targetEntityType)) {
            throw new SchemaValidationException("relationship type '" + definition.getName()
                    + "' does not allow target entity type '" + targetEntityType + "'");
        }
        validateProperties(definition.getProperties(), properties, "relationship type '" + definition.getName() + "'");
    }

    private void validateProperties(
            Iterable<PropertyDefinition> propertyDefinitions, Map<String, Object> properties, String context) {
        for (PropertyDefinition propertyDefinition : propertyDefinitions) {
            Object value = properties.get(propertyDefinition.name());
            if (value == null) {
                if (propertyDefinition.required()) {
                    throw new SchemaValidationException(
                            "missing required property '" + propertyDefinition.name() + "' for " + context);
                }
                continue;
            }
            if (!matchesDataType(value, propertyDefinition.dataType())) {
                throw new SchemaValidationException("property '" + propertyDefinition.name() + "' for " + context
                        + " must be of type " + propertyDefinition.dataType());
            }
        }
    }

    private boolean matchesDataType(Object value, PropertyDataType dataType) {
        return switch (dataType) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case FLOAT -> value instanceof Float || value instanceof Double;
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> isValidIsoDate(value);
        };
    }

    private boolean isValidIsoDate(Object value) {
        if (!(value instanceof String stringValue)) {
            return false;
        }
        try {
            LocalDate.parse(stringValue);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
