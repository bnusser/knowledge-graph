package com.knowledgegraph.core.traversal;

import com.knowledgegraph.core.entity.EntityDto;
import com.knowledgegraph.core.relationship.RelationshipDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ShortestPathResultDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceEntityId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String destinationEntityId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int maxHops,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"outgoing", "incoming", "both"}) String direction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> relationshipTypes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"found", "no_path"}) String outcome,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, minimum = "0", maximum = "10") Integer hopCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EntityDto> entities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<RelationshipDto> relationships) {}
