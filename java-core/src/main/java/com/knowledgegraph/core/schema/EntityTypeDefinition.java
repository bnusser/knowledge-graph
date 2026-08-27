package com.knowledgegraph.core.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import java.util.List;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Schema definition for one entity type (FR-011). {@code propertiesJson} is a JSON-serialized
 * {@code List<PropertyDefinition>}, since Neo4j can't natively store a list of complex objects
 * as a single property; {@link #getProperties()} exposes the parsed form.
 */
@Node("EntityTypeDefinition")
public class EntityTypeDefinition {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final CollectionType PROPERTY_LIST_TYPE =
            MAPPER.getTypeFactory().constructCollectionType(List.class, PropertyDefinition.class);

    @Id
    private String name;

    private String propertiesJson;

    private String identifyingProperty;

    protected EntityTypeDefinition() {
        // required by Spring Data Neo4j
    }

    public EntityTypeDefinition(String name, List<PropertyDefinition> properties, String identifyingProperty) {
        this.name = name;
        setProperties(properties);
        this.identifyingProperty = identifyingProperty;
    }

    public String getName() {
        return name;
    }

    public String getIdentifyingProperty() {
        return identifyingProperty;
    }

    public List<PropertyDefinition> getProperties() {
        try {
            return MAPPER.readValue(propertiesJson, PROPERTY_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt propertiesJson for entity type " + name, e);
        }
    }

    public final void setProperties(List<PropertyDefinition> properties) {
        try {
            this.propertiesJson = MAPPER.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize properties for entity type " + name, e);
        }
    }
}
