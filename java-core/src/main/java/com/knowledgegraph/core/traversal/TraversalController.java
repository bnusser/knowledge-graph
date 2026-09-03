package com.knowledgegraph.core.traversal;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/entities")
public class TraversalController {

    private final TraversalService traversalService;

    public TraversalController(TraversalService traversalService) {
        this.traversalService = traversalService;
    }

    @GetMapping("/{entityId}/neighborhood")
    public NeighborhoodResultDto neighborhood(
            @PathVariable String entityId,
            @RequestParam(required = false) Integer maxHops,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) List<String> relationshipTypes,
            @RequestParam(required = false) Integer limit) {
        return traversalService.neighborhood(entityId, maxHops, direction, relationshipTypes, limit);
    }

    @GetMapping("/{sourceEntityId}/shortest-path/{destinationEntityId}")
    public ShortestPathResultDto shortestPath(
            @PathVariable String sourceEntityId,
            @PathVariable String destinationEntityId,
            @RequestParam(required = false) Integer maxHops,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) List<String> relationshipTypes) {
        return traversalService.shortestPath(
                sourceEntityId, destinationEntityId, maxHops, direction, relationshipTypes);
    }
}
