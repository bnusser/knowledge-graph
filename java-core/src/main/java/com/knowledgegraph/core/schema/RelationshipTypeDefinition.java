package com.knowledgegraph.core.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.util.List;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Schema definition for one relationship type (FR-012): which entity types may serve as its
 * source/target, and its own property rules. {@code propertiesJson} mirrors
 * {@link EntityTypeDefinition}'s JSON-list-of-properties approach.
 */
@Node("RelationshipTypeDefinition")
public class RelationshipTypeDefinition {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CollectionType PROPERTY_LIST_TYPE =
            MAPPER.getTypeFactory().constructCollectionType(List.class, PropertyDefinition.class);

    @Id
    private String name;

    private List<String> allowedSourceTypes;

    private List<String> allowedTargetTypes;

    private String propertiesJson;

    protected RelationshipTypeDefinition() {
        // required by Spring Data Neo4j
    }

    public RelationshipTypeDefinition(
            String name,
            List<String> allowedSourceTypes,
            List<String> allowedTargetTypes,
            List<PropertyDefinition> properties) {
        this.name = name;
        this.allowedSourceTypes = allowedSourceTypes;
        this.allowedTargetTypes = allowedTargetTypes;
        setProperties(properties);
    }

    public String getName() {
        return name;
    }

    public List<String> getAllowedSourceTypes() {
        return allowedSourceTypes;
    }

    public List<String> getAllowedTargetTypes() {
        return allowedTargetTypes;
    }

    public List<PropertyDefinition> getProperties() {
        try {
            return MAPPER.readValue(propertiesJson, PROPERTY_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt propertiesJson for relationship type " + name, e);
        }
    }

    public final void setProperties(List<PropertyDefinition> properties) {
        try {
            this.propertiesJson = MAPPER.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize properties for relationship type " + name, e);
        }
    }
}
