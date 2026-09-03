package com.knowledgegraph.core.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knowledgegraph.core.entity.Entity;
import com.knowledgegraph.core.entity.EntityService;
import com.knowledgegraph.core.schema.RelationshipTypeService;
import com.knowledgegraph.core.traversal.TraversalReader;
import com.knowledgegraph.core.traversal.TraversalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class TraversalLoggingTest {

    private EntityService entityService;
    private TraversalReader traversalReader;
    private TraversalService service;

    @BeforeEach
    void setUp() {
        entityService = mock(EntityService.class);
        RelationshipTypeService relationshipTypeService = mock(RelationshipTypeService.class);
        traversalReader = mock(TraversalReader.class);
        service = new TraversalService(entityService, relationshipTypeService, traversalReader);
        when(relationshipTypeService.normalizeAndValidateFilter(any())).thenReturn(List.of());
        when(traversalReader.findNeighborhoodCandidates(anySet(), any(), anyList(), anySet(), any(Integer.class)))
                .thenReturn(List.of());
        when(traversalReader.findPathCandidates(anySet(), any(), anyList())).thenReturn(List.of());
    }

    @Test
    void emitsExactlyOneSanitizedNeighborhoodCompletionWithoutSecretsOrProperties(CapturedOutput output) {
        when(entityService.getById("unsafe\nidentifier")).thenReturn(
                new Entity("unsafe\nidentifier", "Account", Map.of("secretProperty", "do-not-log")));

        service.neighborhood("unsafe\nidentifier", 1, "both", List.of(), 10);

        String completion = matchingLine(output.getOut(), "operation=neighborhood");
        assertThat(completion).contains("startEntityId=unsafe_identifier", "resultSize=1", "truncated=false");
        assertThat(completion).doesNotContain("secretProperty", "do-not-log", "X-API-Key", "local-dev-key");
        assertThat(output.getOut().lines().filter(line -> line.contains("operation=neighborhood"))).hasSize(1);
    }

    @Test
    void emitsExactlyOneShortestPathCompletionWithoutSecretsOrProperties(CapturedOutput output) {
        when(entityService.getById("A")).thenReturn(new Entity("A", "Account", Map.of("password", "hidden")));
        when(entityService.getById("Z")).thenReturn(new Entity("Z", "Account", Map.of()));

        service.shortestPath("A", "Z", 1, "outgoing", List.of());

        String completion = matchingLine(output.getOut(), "operation=shortest_path");
        assertThat(completion).contains("sourceEntityId=A", "destinationEntityId=Z", "outcome=no_path");
        assertThat(completion).doesNotContain("password", "hidden", "X-API-Key", "local-dev-key");
        assertThat(output.getOut().lines().filter(line -> line.contains("operation=shortest_path"))).hasSize(1);
    }

    private String matchingLine(String output, String marker) {
        return output.lines().filter(line -> line.contains(marker)).findFirst().orElseThrow();
    }
}
