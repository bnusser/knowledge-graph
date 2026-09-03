package com.knowledgegraph.core.traversal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record NeighborhoodResultDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String startEntityId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int maxHops,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"outgoing", "incoming", "both"}) String direction,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> relationshipTypes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int limit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") int resultSize,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean truncated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TraversedEntityDto> entities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TraversedRelationshipDto> relationships) {}
