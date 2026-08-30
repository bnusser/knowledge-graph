package com.knowledgegraph.core.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * A single concept/node in the knowledge graph (FR-001). {@code propertiesJson} is a
 * JSON-serialized property map (mirrors {@code EntityTypeDefinition}'s approach), rather than
 * SDN's {@code @CompositeProperty}, which was found to round-trip scalar values as raw driver
 * {@code Value} wrappers instead of plain Java types. {@code identifyingValue} is a separate,
 * natively-indexable flat property (the canonical string form of the schema's identifying
 * property value) used for fast duplicate detection (FR-014) without needing dynamic Cypher
 * property-key access.
 */
@Node("Entity")
public class Entity {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MapType PROPERTIES_MAP_TYPE =
            MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class);

    @Id
    private String id;

    private String type;

    private String propertiesJson;

    private String identifyingValue;

    protected Entity() {
        // required by Spring Data Neo4j
    }

    public Entity(String id, String type, Map<String, Object> properties) {
        this.id = id;
        this.type = type;
        setProperties(properties != null ? properties : new HashMap<>());
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getIdentifyingValue() {
        return identifyingValue;
    }

    public void setIdentifyingValue(String identifyingValue) {
        this.identifyingValue = identifyingValue;
    }

    public Map<String, Object> getProperties() {
        try {
            return new HashMap<>(MAPPER.readValue(propertiesJson, PROPERTIES_MAP_TYPE));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt propertiesJson for entity " + id, e);
        }
    }

    /** Raw serialized form, for callers building their own Cypher parameters (e.g. batch create). */
    public String getPropertiesJson() {
        return propertiesJson;
    }

    public final void setProperties(Map<String, Object> properties) {
        try {
            this.propertiesJson = MAPPER.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize properties for entity " + id, e);
        }
    }
}
