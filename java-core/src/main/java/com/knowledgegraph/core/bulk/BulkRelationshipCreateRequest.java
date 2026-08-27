package com.knowledgegraph.core.bulk;

import com.knowledgegraph.core.relationship.RelationshipCreateRequest;
import java.util.List;

public record BulkRelationshipCreateRequest(List<RelationshipCreateRequest> items) {
}
