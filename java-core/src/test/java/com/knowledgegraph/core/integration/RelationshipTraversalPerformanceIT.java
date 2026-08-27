package com.knowledgegraph.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledgegraph.core.relationship.RelationshipRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

/**
 * SC-003: single-hop relationship retrieval from either endpoint entity in under 1 second at
 * ~1,000,000 entities / ~5,000,000 relationships. Seeds via batched Cypher (not the REST API —
 * 1M individual HTTP calls would be impractically slow for a test) since this measures graph
 * traversal performance, not the API layer. Tagged so `mvn test` skips it by default; run
 * explicitly with {@code mvn test -Dgroups=perf}.
 */
@Tag("perf")
class RelationshipTraversalPerformanceIT extends AbstractNeo4jIntegrationTest {

    private static final int ENTITY_COUNT = 1_000_000;
    private static final int RELATIONSHIPS_PER_ENTITY = 5;
    private static final int BATCH_SIZE = 5_000;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private RelationshipRepository relationshipRepository;

    @Test
    void singleHopTraversalCompletesUnderOneSecondAtScale() {
        seedEntitiesAndRelationships();

        String probeEntityId = "perf-entity-0";
        long start = System.nanoTime();
        var relationships = relationshipRepository.findByEntity(probeEntityId, "outgoing");
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(relationships).hasSize(RELATIONSHIPS_PER_ENTITY);
        assertThat(elapsedMillis).isLessThan(1_000);
    }

    private void seedEntitiesAndRelationships() {
        neo4jClient.query("CREATE CONSTRAINT perf_entity_id IF NOT EXISTS FOR (e:Entity) REQUIRE e.id IS UNIQUE").run();

        for (int batchStart = 0; batchStart < ENTITY_COUNT; batchStart += BATCH_SIZE) {
            List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
            int batchEnd = Math.min(batchStart + BATCH_SIZE, ENTITY_COUNT);
            for (int i = batchStart; i < batchEnd; i++) {
                batch.add(Map.of("id", "perf-entity-" + i, "type", "PerfEntity"));
            }
            neo4jClient
                    .query("UNWIND $rows AS row CREATE (e:Entity {id: row.id, type: row.type})")
                    .bind(batch)
                    .to("rows")
                    .run();
        }

        for (int batchStart = 0; batchStart < ENTITY_COUNT; batchStart += BATCH_SIZE) {
            List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
            int batchEnd = Math.min(batchStart + BATCH_SIZE, ENTITY_COUNT);
            for (int i = batchStart; i < batchEnd; i++) {
                for (int j = 0; j < RELATIONSHIPS_PER_ENTITY; j++) {
                    int targetIndex = (i + 1 + j) % ENTITY_COUNT;
                    batch.add(Map.of(
                            "id", "perf-rel-" + i + "-" + j,
                            "sourceId", "perf-entity-" + i,
                            "targetId", "perf-entity-" + targetIndex));
                }
            }
            neo4jClient
                    .query("UNWIND $rows AS row "
                            + "MATCH (source:Entity {id: row.sourceId}), (target:Entity {id: row.targetId}) "
                            + "CREATE (source)-[r:RELATES {id: row.id, type: 'PERF_LINK'}]->(target)")
                    .bind(batch)
                    .to("rows")
                    .run();
        }
    }
}
