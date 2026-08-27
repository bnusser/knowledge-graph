package com.knowledgegraph.core.bulk;

import com.knowledgegraph.core.entity.EntityCreateRequest;
import java.util.List;

public record BulkEntityCreateRequest(List<EntityCreateRequest> items) {
}
