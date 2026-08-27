package com.knowledgegraph.core.unit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.knowledgegraph.core.exception.SchemaValidationException;
import com.knowledgegraph.core.schema.EntityTypeDefinition;
import com.knowledgegraph.core.schema.PropertyDataType;
import com.knowledgegraph.core.schema.PropertyDefinition;
import com.knowledgegraph.core.schema.RelationshipTypeDefinition;
import com.knowledgegraph.core.schema.SchemaValidatorImpl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaValidatorImplTest {

    private final SchemaValidatorImpl validator = new SchemaValidatorImpl();

    private final EntityTypeDefinition personType = new EntityTypeDefinition(
            "Person",
            List.of(
                    new PropertyDefinition("name", PropertyDataType.STRING, true),
                    new PropertyDefinition("age", PropertyDataType.INTEGER, false)),
            "name");

    @Test
    void acceptsEntityTypeDefinitionWhenIdentifyingPropertyIsDeclared() {
        assertThatCode(() -> validator.validateEntityTypeDefinition(personType)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEntityTypeDefinitionWhenIdentifyingPropertyIsNotDeclared() {
        EntityTypeDefinition bad = new EntityTypeDefinition(
                "Person", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "ssn");

        assertThatThrownBy(() -> validator.validateEntityTypeDefinition(bad))
                .isInstanceOf(SchemaValidationException.class);
    }

    @Test
    void acceptsEntityPropertiesWithRequiredFieldPresentAndCorrectType() {
        assertThatCode(() -> validator.validateEntityProperties(personType, Map.of("name", "Ada", "age", 30)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEntityPropertiesMissingRequiredField() {
        assertThatThrownBy(() -> validator.validateEntityProperties(personType, Map.of("age", 30)))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsEntityPropertiesWithWrongDataType() {
        assertThatThrownBy(() -> validator.validateEntityProperties(personType, Map.of("name", "Ada", "age", "thirty")))
                .isInstanceOf(SchemaValidationException.class)
                .hasMessageContaining("age");
    }

    @Test
    void rejectsRelationshipWithDisallowedSourceType() {
        RelationshipTypeDefinition worksAt =
                new RelationshipTypeDefinition("WORKS_AT", List.of("Person"), List.of("Organization"), List.of());

        assertThatThrownBy(
                        () -> validator.validateRelationship(worksAt, "Organization", "Organization", Map.of()))
                .isInstanceOf(SchemaValidationException.class);
    }

    @Test
    void acceptsRelationshipWithAllowedSourceAndTargetTypes() {
        RelationshipTypeDefinition worksAt =
                new RelationshipTypeDefinition("WORKS_AT", List.of("Person"), List.of("Organization"), List.of());

        assertThatCode(() -> validator.validateRelationship(worksAt, "Person", "Organization", Map.of()))
                .doesNotThrowAnyException();
    }
}
