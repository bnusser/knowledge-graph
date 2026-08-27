package com.knowledgegraph.core.entity;

import java.util.Map;

public record EntityUpdateRequest(Map<String, Object> properties) {
}
