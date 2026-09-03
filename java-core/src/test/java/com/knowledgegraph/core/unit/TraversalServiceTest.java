package com.knowledgegraph.core.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.knowledgegraph.core.entity.Entity;
import com.knowledgegraph.core.entity.EntityService;
import com.knowledgegraph.core.exception.InvalidTraversalRequestException;
import com.knowledgegraph.core.relationship.Relationship;
import com.knowledgegraph.core.schema.RelationshipTypeService;
import com.knowledgegraph.core.traversal.TraversalDirection;
import com.knowledgegraph.core.traversal.TraversalEdge;
import com.knowledgegraph.core.traversal.TraversalReader;
import com.knowledgegraph.core.traversal.TraversalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TraversalServiceTest {

    private EntityService entityService;
    private RelationshipTypeService relationshipTypeService;
    private TraversalReader traversalRepository;
    private TraversalService service;

    @BeforeEach
    void setUp() {
        entityService = mock(EntityService.class);
        relationshipTypeService = mock(RelationshipTypeService.class);
        traversalRepository = mock(TraversalReader.class);
        service = new TraversalService(entityService, relationshipTypeService, traversalRepository);
        when(relationshipTypeService.normalizeAndValidateFilter(any())).thenReturn(List.of());
    }

    @Test
    void neighborhoodAppliesDefaultsAndReturnsIsolatedStart() {
        when(entityService.getById("A")).thenReturn(entity("A"));
        when(traversalRepository.findNeighborhoodCandidates(anySet(), any(), anyList(), anySet(), anyInt()))
                .thenReturn(List.of());

        var result = service.neighborhood("A", null, null, null, null);

        assertThat(result.maxHops()).isEqualTo(3);
        assertThat(result.direction()).isEqualTo("both");
        assertThat(result.limit()).isEqualTo(100);
        assertThat(result.resultSize()).isEqualTo(1);
        assertThat(result.entities()).extracting(value -> value.id() + ":" + value.distance())
                .containsExactly("A:0");
    }

    @Test
    void zeroHopNeighborhoodNeverReadsRelationships() {
        when(entityService.getById("A")).thenReturn(entity("A"));

        var result = service.neighborhood("A", 0, "outgoing", List.of(), 10);

        assertThat(result.entities()).hasSize(1);
        assertThat(result.relationships()).isEmpty();
        verify(traversalRepository, never()).findNeighborhoodCandidates(anySet(), any(), anyList(), anySet(), anyInt());
    }

    @Test
    void neighborhoodIsCycleSafeAndOrdersBreadthFirstWithEndpointClosure() {
        when(entityService.getById("A")).thenReturn(entity("A"));
        when(traversalRepository.findNeighborhoodCandidates(anySet(), any(), anyList(), anySet(), anyInt()))
                .thenReturn(List.of(edge("A", "C", "r02"), edge("A", "B", "r01"), edge("A", "B", "r07")))
                .thenReturn(List.of(
                        edge("C", "C", "r06"), edge("B", "D", "r03"),
                        edge("C", "D", "r04"), edge("B", "C", "r09")));

        var result = service.neighborhood("A", 2, "both", List.of(), 100);

        assertThat(result.entities()).extracting(value -> value.id() + ":" + value.distance())
                .containsExactly("A:0", "B:1", "C:1", "D:2");
        assertThat(result.relationships())
                .extracting(value -> value.id() + ":" + value.encounteredAtHop())
                .containsExactly("r01:1", "r02:1", "r07:1", "r03:2", "r04:2", "r06:2", "r09:2");
        assertThat(result.resultSize()).isEqualTo(11);
        assertThat(result.truncated()).isFalse();
        assertThat(result.relationships()).allSatisfy(relationship -> assertThat(result.entities())
                .extracting(value -> value.id())
                .contains(relationship.sourceEntityId(), relationship.targetEntityId()));
    }

    @Test
    void neighborhoodStopsBeforeFirstAtomicCandidateThatDoesNotFit() {
        when(entityService.getById("A")).thenReturn(entity("A"));
        when(traversalRepository.findNeighborhoodCandidates(anySet(), any(), anyList(), anySet(), anyInt()))
                .thenReturn(List.of(edge("A", "B", "r01"), edge("A", "C", "r02")));

        var result = service.neighborhood("A", 1, "outgoing", List.of(), 3);

        assertThat(result.resultSize()).isEqualTo(3);
        assertThat(result.entities()).extracting(value -> value.id()).containsExactly("A", "B");
        assertThat(result.relationships()).extracting(value -> value.id()).containsExactly("r01");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void shortestPathUsesMinimumHopsAndLexicographicallySmallestRelationshipSequence() {
        when(entityService.getById("A")).thenReturn(entity("A"));
        when(entityService.getById("D")).thenReturn(entity("D"));
        when(traversalRepository.findPathCandidates(anySet(), any(), anyList()))
                .thenReturn(List.of(edge("A", "C", "r02"), edge("A", "B", "r01")))
                .thenReturn(List.of(edge("C", "D", "r03"), edge("B", "D", "r04")));

        var result = service.shortestPath("A", "D", 3, "outgoing", List.of());

        assertThat(result.outcome()).isEqualTo("found");
        assertThat(result.hopCount()).isEqualTo(2);
        assertThat(result.entities()).extracting(value -> value.id()).containsExactly("A", "B", "D");
        assertThat(result.relationships()).extracting(value -> value.id()).containsExactly("r01", "r04");
    }

    @Test
    void shortestPathHandlesSameEndpointWithoutRepositoryRead() {
        when(entityService.getById("A")).thenReturn(entity("A"));

        var result = service.shortestPath("A", "A", 0, "both", List.of());

        assertThat(result.outcome()).isEqualTo("found");
        assertThat(result.hopCount()).isZero();
        assertThat(result.entities()).extracting(value -> value.id()).containsExactly("A");
        verify(traversalRepository, never()).findPathCandidates(anySet(), any(), anyList());
    }

    @Test
    void shortestPathReturnsNoPathWhenHopBoundExhausted() {
        when(entityService.getById("A")).thenReturn(entity("A"));
        when(entityService.getById("D")).thenReturn(entity("D"));
        when(traversalRepository.findPathCandidates(anySet(), any(), anyList()))
                .thenReturn(List.of(edge("A", "B", "r01")));

        var result = service.shortestPath("A", "D", 1, "both", List.of());

        assertThat(result.outcome()).isEqualTo("no_path");
        assertThat(result.hopCount()).isNull();
        assertThat(result.entities()).isEmpty();
        assertThat(result.relationships()).isEmpty();
    }

    @Test
    void rejectsBoundsBeforeGraphReads() {
        assertThatThrownBy(() -> service.neighborhood("A", -1, "both", List.of(), 1))
                .isInstanceOf(InvalidTraversalRequestException.class);
        assertThatThrownBy(() -> service.neighborhood("A", 11, "both", List.of(), 1))
                .isInstanceOf(InvalidTraversalRequestException.class);
        assertThatThrownBy(() -> service.neighborhood("A", 1, "both", List.of(), 0))
                .isInstanceOf(InvalidTraversalRequestException.class);
        assertThatThrownBy(() -> service.neighborhood("A", 1, "both", List.of(), 1001))
                .isInstanceOf(InvalidTraversalRequestException.class);
        verify(entityService, never()).getById(any());
    }

    @Test
    void echoesCanonicalDirectionAndNormalizedTypesForBothOperations() {
        when(entityService.getById("A")).thenReturn(entity("A"));
        when(entityService.getById("B")).thenReturn(entity("B"));
        when(relationshipTypeService.normalizeAndValidateFilter(List.of("Z", "A", "A")))
                .thenReturn(List.of("A", "Z"));
        when(traversalRepository.findNeighborhoodCandidates(anySet(), any(), anyList(), anySet(), anyInt()))
                .thenReturn(List.of());
        when(traversalRepository.findPathCandidates(anySet(), any(), anyList())).thenReturn(List.of());

        var neighborhood = service.neighborhood("A", 1, "InCoMiNg", List.of("Z", "A", "A"), 10);
        var path = service.shortestPath("A", "B", 1, "InCoMiNg", List.of("Z", "A", "A"));

        assertThat(neighborhood.direction()).isEqualTo("incoming");
        assertThat(neighborhood.relationshipTypes()).containsExactly("A", "Z");
        assertThat(path.direction()).isEqualTo("incoming");
        assertThat(path.relationshipTypes()).containsExactly("A", "Z");
        verify(traversalRepository).findPathCandidates(anySet(), org.mockito.ArgumentMatchers.eq(TraversalDirection.INCOMING),
                org.mockito.ArgumentMatchers.eq(List.of("A", "Z")));
    }

    private Entity entity(String id) {
        return new Entity(id, "Node", Map.of("name", id));
    }

    private TraversalEdge edge(String current, String next, String relationshipId) {
        Entity currentEntity = entity(current);
        Entity nextEntity = entity(next);
        Relationship relationship = new Relationship(
                relationshipId, "LINK", current, next, Map.of("weight", 1));
        return new TraversalEdge(current, next, currentEntity, nextEntity, relationship);
    }
}
