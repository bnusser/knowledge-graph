package com.knowledgegraph.core.relationship;

import java.util.HashMap;
import java.util.Map;

/**
 * A directed, typed connection between two entities (FR-006). Not a Spring Data Neo4j
 * {@code @Node}/{@code @RelationshipProperties} type: relationship "type" is user-defined at
 * runtime (per schema), so persistence uses a single native Neo4j relationship type
 * ({@code :RELATES}) with {@code type} as a bound property, executed via {@code Neo4jClient}
 * (see {@link RelationshipRepository}) instead of SDN's compile-time-typed relationship mapping.
 */
public class Relationship {

    private final String id;
    private final String type;
    private final String sourceEntityId;
    private final String targetEntityId;
    private final Map<String, Object> properties;

    public Relationship(
            String id, String type, String sourceEntityId, String targetEntityId, Map<String, Object> properties) {
        this.id = id;
        this.type = type;
        this.sourceEntityId = sourceEntityId;
        this.targetEntityId = targetEntityId;
        this.properties = properties != null ? new HashMap<>(properties) : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getSourceEntityId() {
        return sourceEntityId;
    }

    public String getTargetEntityId() {
        return targetEntityId;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
