package com.knowledgegraph.core.entity;

import java.util.Map;

public record EntityDto(String id, String type, Map<String, Object> properties) {

    public static EntityDto from(Entity entity) {
        return new EntityDto(entity.getId(), entity.getType(), entity.getProperties());
    }
}
