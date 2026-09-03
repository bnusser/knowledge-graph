package com.knowledgegraph.core.traversal;

import com.knowledgegraph.core.entity.Entity;
import com.knowledgegraph.core.relationship.Relationship;

/** One traversable relationship step with native endpoints and request-relative orientation. */
public record TraversalEdge(
        String currentEntityId,
        String nextEntityId,
        Entity sourceEntity,
        Entity targetEntity,
        Relationship relationship) {}
