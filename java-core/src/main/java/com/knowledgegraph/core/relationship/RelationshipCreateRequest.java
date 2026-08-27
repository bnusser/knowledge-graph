package com.knowledgegraph.core.relationship;

import java.util.Map;

public record RelationshipCreateRequest(String type, String sourceEntityId, String targetEntityId, Map<String, Object> properties) {
}
