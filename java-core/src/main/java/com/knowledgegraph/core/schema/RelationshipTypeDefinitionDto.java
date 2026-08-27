package com.knowledgegraph.core.schema;

import java.util.List;

/** Request/response shape for relationship type definitions (contracts/openapi.yaml RelationshipTypeDefinition). */
public record RelationshipTypeDefinitionDto(
        String name, List<String> allowedSourceTypes, List<String> allowedTargetTypes, List<PropertyDefinition> properties) {

    public static RelationshipTypeDefinitionDto from(RelationshipTypeDefinition definition) {
        return new RelationshipTypeDefinitionDto(
                definition.getName(),
                definition.getAllowedSourceTypes(),
                definition.getAllowedTargetTypes(),
                definition.getProperties());
    }
}
