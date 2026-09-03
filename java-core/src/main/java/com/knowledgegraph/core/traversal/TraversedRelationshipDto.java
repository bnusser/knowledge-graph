package com.knowledgegraph.core.traversal;

import com.knowledgegraph.core.relationship.Relationship;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record TraversedRelationshipDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceEntityId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String targetEntityId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> properties,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1", maximum = "10") int encounteredAtHop) {

    public static TraversedRelationshipDto from(Relationship relationship, int encounteredAtHop) {
        return new TraversedRelationshipDto(
                relationship.getId(),
                relationship.getType(),
                relationship.getSourceEntityId(),
                relationship.getTargetEntityId(),
                relationship.getProperties(),
                encounteredAtHop);
    }
}
