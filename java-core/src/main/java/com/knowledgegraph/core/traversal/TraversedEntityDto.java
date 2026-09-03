package com.knowledgegraph.core.traversal;

import com.knowledgegraph.core.entity.Entity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record TraversedEntityDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> properties,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") int distance) {

    public static TraversedEntityDto from(Entity entity, int distance) {
        return new TraversedEntityDto(entity.getId(), entity.getType(), entity.getProperties(), distance);
    }
}
