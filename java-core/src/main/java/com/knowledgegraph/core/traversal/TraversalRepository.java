package com.knowledgegraph.core.traversal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegraph.core.entity.Entity;
import com.knowledgegraph.core.relationship.Relationship;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.TypeSystem;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

/** Parameterized one-hop read queries used by application-level breadth-first traversal. */
@Repository
public class TraversalRepository implements TraversalReader {

    private static final TypeReference<Map<String, Object>> PROPERTY_MAP = new TypeReference<>() {};

    private static final String FILTER =
            "WHERE NOT r.id IN $seenRelationshipIds AND (size($relationshipTypes) = 0 OR r.type IN $relationshipTypes) ";
    private static final String PATH_FILTER =
            "WHERE size($relationshipTypes) = 0 OR r.type IN $relationshipTypes ";
    private static final String RETURN_CANDIDATE =
            "RETURN source, target, r, currentId, nextId ORDER BY r.id LIMIT $candidateLimit";
    private static final String RETURN_PATH =
            "RETURN source, target, r, current.id AS currentId, next.id AS nextId ORDER BY current.id, r.id, next.id";

    private final Neo4jClient neo4jClient;
    private final ObjectMapper objectMapper;

    public TraversalRepository(Neo4jClient neo4jClient, ObjectMapper objectMapper) {
        this.neo4jClient = neo4jClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TraversalEdge> findNeighborhoodCandidates(
            Set<String> frontierIds,
            TraversalDirection direction,
            List<String> relationshipTypes,
            Set<String> seenRelationshipIds,
            int candidateLimit) {
        if (frontierIds.isEmpty() || candidateLimit <= 0) {
            return List.of();
        }
        String query = switch (direction) {
            case OUTGOING -> "UNWIND $frontierIds AS frontierId "
                    + "MATCH (source:Entity {id: frontierId})-[r:RELATES]->(target:Entity) "
                    + FILTER
                    + "WITH DISTINCT source, target, r, source.id AS currentId, target.id AS nextId "
                    + RETURN_CANDIDATE;
            case INCOMING -> "UNWIND $frontierIds AS frontierId "
                    + "MATCH (source:Entity)-[r:RELATES]->(target:Entity {id: frontierId}) "
                    + FILTER
                    + "WITH DISTINCT source, target, r, target.id AS currentId, source.id AS nextId "
                    + RETURN_CANDIDATE;
            case BOTH -> "UNWIND $frontierIds AS frontierId "
                    + "MATCH (current:Entity {id: frontierId})-[r:RELATES]-(next:Entity) "
                    + FILTER
                    + "WITH DISTINCT r, startNode(r) AS source, endNode(r) AS target "
                    + "WITH source, target, r, "
                    + "CASE WHEN source.id IN $frontierIds THEN source.id ELSE target.id END AS currentId, "
                    + "CASE WHEN source.id IN $frontierIds THEN target.id ELSE source.id END AS nextId "
                    + RETURN_CANDIDATE;
        };
        return execute(query, frontierIds, relationshipTypes, seenRelationshipIds, candidateLimit);
    }

    @Override
    public List<TraversalEdge> findPathCandidates(
            Set<String> frontierIds, TraversalDirection direction, List<String> relationshipTypes) {
        if (frontierIds.isEmpty()) {
            return List.of();
        }
        String query = switch (direction) {
            case OUTGOING -> "UNWIND $frontierIds AS frontierId "
                    + "MATCH (current:Entity {id: frontierId})-[r:RELATES]->(next:Entity) "
                    + PATH_FILTER
                    + "WITH DISTINCT current, next, r, current AS source, next AS target "
                    + RETURN_PATH;
            case INCOMING -> "UNWIND $frontierIds AS frontierId "
                    + "MATCH (next:Entity)-[r:RELATES]->(current:Entity {id: frontierId}) "
                    + PATH_FILTER
                    + "WITH DISTINCT current, next, r, next AS source, current AS target "
                    + RETURN_PATH;
            case BOTH -> "UNWIND $frontierIds AS frontierId "
                    + "MATCH (current:Entity {id: frontierId})-[r:RELATES]-(next:Entity) "
                    + PATH_FILTER
                    + "WITH DISTINCT current, next, r, startNode(r) AS source, endNode(r) AS target "
                    + RETURN_PATH;
        };
        return neo4jClient
                .query(query)
                .bind(new ArrayList<>(frontierIds))
                .to("frontierIds")
                .bind(relationshipTypes)
                .to("relationshipTypes")
                .fetchAs(TraversalEdge.class)
                .mappedBy(this::toEdge)
                .all()
                .stream()
                .toList();
    }

    private List<TraversalEdge> execute(
            String query,
            Set<String> frontierIds,
            List<String> relationshipTypes,
            Set<String> seenRelationshipIds,
            int candidateLimit) {
        return neo4jClient
                .query(query)
                .bind(new ArrayList<>(frontierIds))
                .to("frontierIds")
                .bind(relationshipTypes)
                .to("relationshipTypes")
                .bind(new ArrayList<>(seenRelationshipIds))
                .to("seenRelationshipIds")
                .bind(candidateLimit)
                .to("candidateLimit")
                .fetchAs(TraversalEdge.class)
                .mappedBy(this::toEdge)
                .all()
                .stream()
                .toList();
    }

    private TraversalEdge toEdge(TypeSystem typeSystem, Record record) {
        Node sourceNode = record.get("source").asNode();
        Node targetNode = record.get("target").asNode();
        Entity source = toEntity(sourceNode);
        Entity target = toEntity(targetNode);
        var stored = record.get("r").asRelationship();
        Map<String, Object> properties = new HashMap<>(stored.asMap());
        properties.remove("id");
        properties.remove("type");
        Relationship relationship = new Relationship(
                stored.get("id").asString(),
                stored.get("type").asString(),
                source.getId(),
                target.getId(),
                properties);
        return new TraversalEdge(
                record.get("currentId").asString(),
                record.get("nextId").asString(),
                source,
                target,
                relationship);
    }

    private Entity toEntity(Node node) {
        String propertiesJson = node.containsKey("propertiesJson")
                ? node.get("propertiesJson").asString("{}")
                : "{}";
        try {
            return new Entity(
                    node.get("id").asString(),
                    node.get("type").asString(),
                    objectMapper.readValue(propertiesJson, PROPERTY_MAP));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Corrupt propertiesJson for entity " + node.get("id").asString(), exception);
        }
    }
}
