package com.knowledgegraph.core.relationship;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.neo4j.driver.Record;
import org.neo4j.driver.types.TypeSystem;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

/**
 * Persistence for {@link Relationship}. Uses {@link Neo4jClient} with fully parameterized Cypher
 * (never string-concatenated) rather than a Spring Data Neo4j {@code Neo4jRepository}, because
 * the actual Neo4j relationship type used in the graph is always the fixed {@code :RELATES} —
 * the user-defined semantic "type" (e.g. "WORKS_AT") is a bound property, not part of the
 * relationship's structural type, which sidesteps Cypher's inability to parameterize
 * relationship types/labels directly (Constitution Principle IV).
 */
@Repository
public class RelationshipRepository {

    private final Neo4jClient neo4jClient;

    public RelationshipRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public Relationship create(
            String id, String type, String sourceEntityId, String targetEntityId, Map<String, Object> properties) {
        String cypher =
                "MATCH (source:Entity {id: $sourceId}) "
                        + "MATCH (target:Entity {id: $targetId}) "
                        + "CREATE (source)-[r:RELATES {id: $id, type: $type}]->(target) "
                        + "SET r += $properties "
                        + "RETURN r, source.id AS sourceId, target.id AS targetId";
        return neo4jClient
                .query(cypher)
                .bind(id)
                .to("id")
                .bind(type)
                .to("type")
                .bind(sourceEntityId)
                .to("sourceId")
                .bind(targetEntityId)
                .to("targetId")
                .bind(properties)
                .to("properties")
                .fetchAs(Relationship.class)
                .mappedBy(this::toRelationship)
                .one()
                .orElseThrow(() -> new IllegalStateException("Failed to create relationship " + id));
    }

    /**
     * Creates many relationships in a single UNWIND-based statement — one transaction/commit
     * for the whole batch rather than one per relationship — since per-item commit overhead
     * dominates bulk-load cost. {@code rows} entries need keys: id, type, sourceId, targetId,
     * properties. Re-maps results by the caller-supplied {@code id} rather than trusting
     * result-row order, since Cypher doesn't guarantee UNWIND row order is preserved on return.
     */
    public List<Relationship> createBatch(List<Map<String, Object>> rows) {
        String cypher = "UNWIND $rows AS row "
                + "MATCH (source:Entity {id: row.sourceId}) "
                + "MATCH (target:Entity {id: row.targetId}) "
                + "CREATE (source)-[r:RELATES {id: row.id, type: row.type}]->(target) "
                + "SET r += row.properties "
                + "RETURN r, source.id AS sourceId, target.id AS targetId";
        List<Relationship> created = neo4jClient
                .query(cypher)
                .bind(rows)
                .to("rows")
                .fetchAs(Relationship.class)
                .mappedBy(this::toRelationship)
                .all()
                .stream()
                .toList();
        Map<String, Relationship> byId = created.stream().collect(java.util.stream.Collectors.toMap(Relationship::getId, r -> r));
        return rows.stream().map(row -> byId.get((String) row.get("id"))).toList();
    }

    public Optional<Relationship> findById(String id) {
        String cypher = "MATCH (source)-[r:RELATES {id: $id}]->(target) "
                + "RETURN r, source.id AS sourceId, target.id AS targetId";
        return neo4jClient
                .query(cypher)
                .bind(id)
                .to("id")
                .fetchAs(Relationship.class)
                .mappedBy(this::toRelationship)
                .one();
    }

    public List<Relationship> findByEntity(String entityId, String direction) {
        String cypher = switch (direction) {
            case "outgoing" -> "MATCH (e:Entity {id: $entityId})-[r:RELATES]->(other:Entity) "
                    + "RETURN r, e.id AS sourceId, other.id AS targetId";
            case "incoming" -> "MATCH (other:Entity)-[r:RELATES]->(e:Entity {id: $entityId}) "
                    + "RETURN r, other.id AS sourceId, e.id AS targetId";
            default -> "MATCH (e:Entity {id: $entityId})-[r:RELATES]-(other:Entity) "
                    + "RETURN r, "
                    + "CASE WHEN startNode(r) = e THEN e.id ELSE other.id END AS sourceId, "
                    + "CASE WHEN startNode(r) = e THEN other.id ELSE e.id END AS targetId";
        };
        return neo4jClient
                .query(cypher)
                .bind(entityId)
                .to("entityId")
                .fetchAs(Relationship.class)
                .mappedBy(this::toRelationship)
                .all()
                .stream()
                .toList();
    }

    public Optional<Relationship> updateProperties(String id, Map<String, Object> properties) {
        String cypher = "MATCH (source)-[r:RELATES {id: $id}]->(target) "
                + "SET r += $properties "
                + "RETURN r, source.id AS sourceId, target.id AS targetId";
        return neo4jClient
                .query(cypher)
                .bind(id)
                .to("id")
                .bind(properties)
                .to("properties")
                .fetchAs(Relationship.class)
                .mappedBy(this::toRelationship)
                .one();
    }

    public void deleteById(String id) {
        neo4jClient
                .query("MATCH ()-[r:RELATES {id: $id}]->() DELETE r")
                .bind(id)
                .to("id")
                .run();
    }

    /** Deletes every relationship touching the given entity (cascade delete, FR-005). */
    public void deleteAllForEntity(String entityId) {
        neo4jClient
                .query("MATCH (e:Entity {id: $entityId})-[r:RELATES]-() DELETE r")
                .bind(entityId)
                .to("entityId")
                .run();
    }

    /** Counts relationships touching the given entity (used to block deletion, FR-005). */
    public long countForEntity(String entityId) {
        return neo4jClient
                .query("MATCH (e:Entity {id: $entityId})-[r:RELATES]-() RETURN count(r) AS c")
                .bind(entityId)
                .to("entityId")
                .fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("c").asLong())
                .one()
                .orElse(0L);
    }

    private Relationship toRelationship(TypeSystem typeSystem, Record record) {
        var relationship = record.get("r").asRelationship();
        Map<String, Object> properties = new HashMap<>(relationship.asMap());
        properties.remove("id");
        properties.remove("type");
        return new Relationship(
                relationship.get("id").asString(),
                relationship.get("type").asString(),
                record.get("sourceId").asString(),
                record.get("targetId").asString(),
                properties);
    }
}
