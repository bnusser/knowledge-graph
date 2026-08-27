package com.knowledgegraph.core.entity;

import java.util.Map;

public record EntityCreateRequest(String type, Map<String, Object> properties) {
}
