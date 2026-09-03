package com.knowledgegraph.core.traversal;

import com.knowledgegraph.core.entity.Entity;
import com.knowledgegraph.core.entity.EntityDto;
import com.knowledgegraph.core.exception.InvalidTraversalRequestException;
import com.knowledgegraph.core.relationship.RelationshipDto;
import com.knowledgegraph.core.schema.RelationshipTypeService;
import com.knowledgegraph.core.entity.EntityService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TraversalService {

    public static final int DEFAULT_MAX_HOPS = 3;
    public static final int MAX_MAX_HOPS = 10;
    public static final int DEFAULT_NEIGHBORHOOD_LIMIT = 100;
    public static final int MAX_NEIGHBORHOOD_LIMIT = 1_000;

    private static final Logger LOG = LoggerFactory.getLogger(TraversalService.class);

    private final EntityService entityService;
    private final RelationshipTypeService relationshipTypeService;
    private final TraversalReader traversalRepository;

    public TraversalService(
            EntityService entityService,
            RelationshipTypeService relationshipTypeService,
            TraversalReader traversalRepository) {
        this.entityService = entityService;
        this.relationshipTypeService = relationshipTypeService;
        this.traversalRepository = traversalRepository;
    }

    public NeighborhoodResultDto neighborhood(
            String startEntityId,
            Integer requestedMaxHops,
            String requestedDirection,
            List<String> requestedRelationshipTypes,
            Integer requestedLimit) {
        long started = System.nanoTime();
        int maxHops = normalizeMaxHops(requestedMaxHops);
        int limit = normalizeLimit(requestedLimit);
        TraversalDirection direction = TraversalDirection.parse(requestedDirection);
        List<String> relationshipTypes = relationshipTypeService.normalizeAndValidateFilter(requestedRelationshipTypes);
        Entity start = entityService.getById(startEntityId);

        Map<String, EntityAtDistance> entities = new LinkedHashMap<>();
        Map<String, RelationshipAtHop> relationships = new LinkedHashMap<>();
        entities.put(start.getId(), new EntityAtDistance(start, 0));
        Set<String> frontier = new LinkedHashSet<>(Set.of(start.getId()));
        boolean truncated = false;

        for (int depth = 0; depth < maxHops && !frontier.isEmpty() && !truncated; depth++) {
            int remaining = limit - entities.size() - relationships.size();
            List<TraversalEdge> candidates = traversalRepository.findNeighborhoodCandidates(
                    frontier,
                    direction,
                    relationshipTypes,
                    relationships.keySet(),
                    Math.max(1, remaining + 1));
            candidates = candidates.stream()
                    .sorted(Comparator.comparing(edge -> edge.relationship().getId()))
                    .toList();
            Set<String> nextFrontier = new LinkedHashSet<>();
            for (TraversalEdge edge : candidates) {
                int cost = relationships.containsKey(edge.relationship().getId()) ? 0 : 1;
                if (!entities.containsKey(edge.sourceEntity().getId())) {
                    cost++;
                }
                if (!entities.containsKey(edge.targetEntity().getId())) {
                    cost++;
                }
                remaining = limit - entities.size() - relationships.size();
                if (cost > remaining) {
                    truncated = true;
                    break;
                }
                int encounteredAtHop = depth + 1;
                relationships.putIfAbsent(
                        edge.relationship().getId(), new RelationshipAtHop(edge, encounteredAtHop));
                addEntity(entities, edge.sourceEntity(), encounteredAtHop, nextFrontier);
                addEntity(entities, edge.targetEntity(), encounteredAtHop, nextFrontier);
            }
            frontier = nextFrontier;
        }

        List<TraversedEntityDto> entityDtos = entities.values().stream()
                .sorted(Comparator.comparingInt(EntityAtDistance::distance)
                        .thenComparing(value -> value.entity().getId()))
                .map(value -> TraversedEntityDto.from(value.entity(), value.distance()))
                .toList();
        List<TraversedRelationshipDto> relationshipDtos = relationships.values().stream()
                .sorted(Comparator.comparingInt(RelationshipAtHop::hop)
                        .thenComparing(value -> value.edge().relationship().getId()))
                .map(value -> TraversedRelationshipDto.from(value.edge().relationship(), value.hop()))
                .toList();
        int resultSize = entityDtos.size() + relationshipDtos.size();
        NeighborhoodResultDto result = new NeighborhoodResultDto(
                startEntityId,
                maxHops,
                direction.apiValue(),
                relationshipTypes,
                limit,
                resultSize,
                truncated,
                entityDtos,
                relationshipDtos);
        logNeighborhood(result, started);
        return result;
    }

    public ShortestPathResultDto shortestPath(
            String sourceEntityId,
            String destinationEntityId,
            Integer requestedMaxHops,
            String requestedDirection,
            List<String> requestedRelationshipTypes) {
        long started = System.nanoTime();
        int maxHops = normalizeMaxHops(requestedMaxHops);
        TraversalDirection direction = TraversalDirection.parse(requestedDirection);
        List<String> relationshipTypes = relationshipTypeService.normalizeAndValidateFilter(requestedRelationshipTypes);
        Entity source = entityService.getById(sourceEntityId);
        Entity destination = entityService.getById(destinationEntityId);

        if (sourceEntityId.equals(destinationEntityId)) {
            ShortestPathResultDto result = foundResult(
                    sourceEntityId, destinationEntityId, maxHops, direction, relationshipTypes,
                    List.of(source), List.of());
            logShortestPath(result, started);
            return result;
        }

        Map<String, PathState> discovered = new HashMap<>();
        discovered.put(sourceEntityId, new PathState(source, null, null, List.of()));
        Set<String> frontier = new LinkedHashSet<>(Set.of(sourceEntityId));

        for (int depth = 0; depth < maxHops && !frontier.isEmpty(); depth++) {
            List<PathCandidate> candidates = new ArrayList<>();
            for (TraversalEdge edge : traversalRepository.findPathCandidates(frontier, direction, relationshipTypes)) {
                PathState parent = discovered.get(edge.currentEntityId());
                if (parent == null || discovered.containsKey(edge.nextEntityId())) {
                    continue;
                }
                List<String> sequence = new ArrayList<>(parent.relationshipIds());
                sequence.add(edge.relationship().getId());
                candidates.add(new PathCandidate(edge, List.copyOf(sequence)));
            }
            candidates.sort((left, right) -> {
                int compared = compareSequences(left.relationshipIds(), right.relationshipIds());
                return compared != 0 ? compared : left.edge().nextEntityId().compareTo(right.edge().nextEntityId());
            });
            Set<String> nextFrontier = new LinkedHashSet<>();
            for (PathCandidate candidate : candidates) {
                String nextId = candidate.edge().nextEntityId();
                if (discovered.containsKey(nextId)) {
                    continue;
                }
                Entity next = entityFor(candidate.edge(), nextId);
                PathState state = new PathState(
                        next, candidate.edge().currentEntityId(), candidate.edge(), candidate.relationshipIds());
                discovered.put(nextId, state);
                nextFrontier.add(nextId);
                if (nextId.equals(destinationEntityId)) {
                    ShortestPathResultDto result = reconstruct(
                            sourceEntityId, destinationEntityId, maxHops, direction, relationshipTypes, discovered);
                    logShortestPath(result, started);
                    return result;
                }
            }
            frontier = nextFrontier;
        }

        ShortestPathResultDto result = new ShortestPathResultDto(
                sourceEntityId,
                destinationEntityId,
                maxHops,
                direction.apiValue(),
                relationshipTypes,
                "no_path",
                null,
                List.of(),
                List.of());
        logShortestPath(result, started);
        return result;
    }

    private ShortestPathResultDto reconstruct(
            String sourceEntityId,
            String destinationEntityId,
            int maxHops,
            TraversalDirection direction,
            List<String> relationshipTypes,
            Map<String, PathState> discovered) {
        List<Entity> reverseEntities = new ArrayList<>();
        List<TraversalEdge> reverseEdges = new ArrayList<>();
        String current = destinationEntityId;
        while (current != null) {
            PathState state = discovered.get(current);
            reverseEntities.add(state.entity());
            if (state.edge() != null) {
                reverseEdges.add(state.edge());
            }
            current = state.previousEntityId();
        }
        java.util.Collections.reverse(reverseEntities);
        java.util.Collections.reverse(reverseEdges);
        return foundResult(
                sourceEntityId,
                destinationEntityId,
                maxHops,
                direction,
                relationshipTypes,
                reverseEntities,
                reverseEdges.stream().map(TraversalEdge::relationship).toList());
    }

    private ShortestPathResultDto foundResult(
            String sourceEntityId,
            String destinationEntityId,
            int maxHops,
            TraversalDirection direction,
            List<String> relationshipTypes,
            List<Entity> entities,
            List<com.knowledgegraph.core.relationship.Relationship> relationships) {
        return new ShortestPathResultDto(
                sourceEntityId,
                destinationEntityId,
                maxHops,
                direction.apiValue(),
                relationshipTypes,
                "found",
                relationships.size(),
                entities.stream().map(EntityDto::from).toList(),
                relationships.stream().map(RelationshipDto::from).toList());
    }

    private void addEntity(
            Map<String, EntityAtDistance> entities,
            Entity entity,
            int distance,
            Set<String> nextFrontier) {
        if (!entities.containsKey(entity.getId())) {
            entities.put(entity.getId(), new EntityAtDistance(entity, distance));
            nextFrontier.add(entity.getId());
        }
    }

    private Entity entityFor(TraversalEdge edge, String entityId) {
        return edge.sourceEntity().getId().equals(entityId) ? edge.sourceEntity() : edge.targetEntity();
    }

    private int normalizeMaxHops(Integer value) {
        int normalized = value == null ? DEFAULT_MAX_HOPS : value;
        if (normalized < 0 || normalized > MAX_MAX_HOPS) {
            throw new InvalidTraversalRequestException("maxHops must be between 0 and 10");
        }
        return normalized;
    }

    private int normalizeLimit(Integer value) {
        int normalized = value == null ? DEFAULT_NEIGHBORHOOD_LIMIT : value;
        if (normalized < 1 || normalized > MAX_NEIGHBORHOOD_LIMIT) {
            throw new InvalidTraversalRequestException("limit must be between 1 and 1000");
        }
        return normalized;
    }

    private int compareSequences(List<String> left, List<String> right) {
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            int compared = left.get(index).compareTo(right.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private void logNeighborhood(NeighborhoodResultDto result, long started) {
        LOG.info(
                "operation=neighborhood startEntityId={} maxHops={} direction={} relationshipTypeCount={} "
                        + "entityCount={} relationshipCount={} resultSize={} truncated={} outcome=completed elapsedMs={}",
                sanitize(result.startEntityId()),
                result.maxHops(),
                result.direction(),
                result.relationshipTypes().size(),
                result.entities().size(),
                result.relationships().size(),
                result.resultSize(),
                result.truncated(),
                elapsedMillis(started));
    }

    private void logShortestPath(ShortestPathResultDto result, long started) {
        LOG.info(
                "operation=shortest_path sourceEntityId={} destinationEntityId={} maxHops={} direction={} "
                        + "relationshipTypeCount={} entityCount={} relationshipCount={} outcome={} elapsedMs={}",
                sanitize(result.sourceEntityId()),
                sanitize(result.destinationEntityId()),
                result.maxHops(),
                result.direction(),
                result.relationshipTypes().size(),
                result.entities().size(),
                result.relationships().size(),
                result.outcome(),
                elapsedMillis(started));
    }

    private String sanitize(String value) {
        return value == null ? "null" : value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private record EntityAtDistance(Entity entity, int distance) {}

    private record RelationshipAtHop(TraversalEdge edge, int hop) {}

    private record PathState(Entity entity, String previousEntityId, TraversalEdge edge, List<String> relationshipIds) {}

    private record PathCandidate(TraversalEdge edge, List<String> relationshipIds) {}
}
