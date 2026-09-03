package com.knowledgegraph.core.traversal;

import java.util.List;
import java.util.Set;

public interface TraversalReader {

    List<TraversalEdge> findNeighborhoodCandidates(
            Set<String> frontierIds,
            TraversalDirection direction,
            List<String> relationshipTypes,
            Set<String> seenRelationshipIds,
            int candidateLimit);

    List<TraversalEdge> findPathCandidates(
            Set<String> frontierIds, TraversalDirection direction, List<String> relationshipTypes);
}
