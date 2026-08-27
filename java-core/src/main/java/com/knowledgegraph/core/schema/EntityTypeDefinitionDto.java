package com.knowledgegraph.core.schema;

import java.util.List;

/** Request/response shape for entity type definitions (contracts/openapi.yaml EntityTypeDefinition). */
public record EntityTypeDefinitionDto(String name, List<PropertyDefinition> properties, String identifyingProperty) {

    public static EntityTypeDefinitionDto from(EntityTypeDefinition definition) {
        return new EntityTypeDefinitionDto(
                definition.getName(), definition.getProperties(), definition.getIdentifyingProperty());
    }
}
