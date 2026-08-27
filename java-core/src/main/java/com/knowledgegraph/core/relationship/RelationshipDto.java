package com.knowledgegraph.core.relationship;

import java.util.Map;

public record RelationshipDto(String id, String type, String sourceEntityId, String targetEntityId, Map<String, Object> properties) {

    public static RelationshipDto from(Relationship relationship) {
        return new RelationshipDto(
                relationship.getId(),
                relationship.getType(),
                relationship.getSourceEntityId(),
                relationship.getTargetEntityId(),
                relationship.getProperties());
    }
}
