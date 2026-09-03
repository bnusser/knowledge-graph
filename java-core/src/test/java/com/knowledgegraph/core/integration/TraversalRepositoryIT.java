package com.knowledgegraph.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledgegraph.core.traversal.TraversalDirection;
import com.knowledgegraph.core.traversal.TraversalRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

class TraversalRepositoryIT extends AbstractNeo4jIntegrationTest {

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private TraversalRepository repository;

    @BeforeEach
    void seedGraph() {
        neo4jClient
                .query("CREATE (a:Entity {id:'A', type:'Node', propertiesJson:'{}'}), "
                        + "(b:Entity {id:'B', type:'Node', propertiesJson:'{}'}), "
                        + "(c:Entity {id:'C', type:'Node', propertiesJson:'{}'}), "
                        + "(a)-[:RELATES {id:'r01', type:'ALPHA'}]->(b), "
                        + "(c)-[:RELATES {id:'r02', type:'BETA'}]->(a), "
                        + "(a)-[:RELATES {id:'r03', type:'ALPHA'}]->(c)")
                .run();
    }

    @Test
    void readsNativeDirectionsAndSemanticTypeFilters() {
        assertThat(repository.findNeighborhoodCandidates(
                        Set.of("A"), TraversalDirection.OUTGOING, List.of(), Set.of(), 10))
                .extracting(edge -> edge.relationship().getId())
                .containsExactly("r01", "r03");
        assertThat(repository.findNeighborhoodCandidates(
                        Set.of("A"), TraversalDirection.INCOMING, List.of(), Set.of(), 10))
                .extracting(edge -> edge.relationship().getId())
                .containsExactly("r02");
        assertThat(repository.findNeighborhoodCandidates(
                        Set.of("A"), TraversalDirection.BOTH, List.of("BETA"), Set.of(), 10))
                .extracting(edge -> edge.relationship().getId())
                .containsExactly("r02");
    }

    @Test
    void excludesSeenRelationshipsAndHonorsCandidateCap() {
        assertThat(repository.findNeighborhoodCandidates(
                        Set.of("A"), TraversalDirection.BOTH, List.of(), Set.of("r01"), 1))
                .extracting(edge -> edge.relationship().getId())
                .containsExactly("r02");
    }

    @Test
    void pathRowsRetainRequestRelativeOrientation() {
        var incoming = repository.findPathCandidates(Set.of("A"), TraversalDirection.INCOMING, List.of());

        assertThat(incoming).singleElement().satisfies(edge -> {
            assertThat(edge.currentEntityId()).isEqualTo("A");
            assertThat(edge.nextEntityId()).isEqualTo("C");
            assertThat(edge.relationship().getSourceEntityId()).isEqualTo("C");
            assertThat(edge.relationship().getTargetEntityId()).isEqualTo("A");
        });
    }
}
