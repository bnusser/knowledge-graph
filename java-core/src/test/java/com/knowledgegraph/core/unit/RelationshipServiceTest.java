package com.knowledgegraph.core.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.knowledgegraph.core.entity.Entity;
import com.knowledgegraph.core.entity.EntityService;
import com.knowledgegraph.core.exception.NotFoundException;
import com.knowledgegraph.core.exception.SchemaValidationException;
import com.knowledgegraph.core.relationship.Relationship;
import com.knowledgegraph.core.relationship.RelationshipCreateRequest;
import com.knowledgegraph.core.relationship.RelationshipRepository;
import com.knowledgegraph.core.relationship.RelationshipService;
import com.knowledgegraph.core.schema.RelationshipTypeDefinition;
import com.knowledgegraph.core.schema.RelationshipTypeService;
import com.knowledgegraph.core.schema.SchemaValidator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelationshipServiceTest {

    private RelationshipRepository relationshipRepository;
    private RelationshipTypeService relationshipTypeService;
    private EntityService entityService;
    private SchemaValidator schemaValidator;
    private RelationshipService relationshipService;

    private final RelationshipTypeDefinition worksAt =
            new RelationshipTypeDefinition("WORKS_AT", List.of("Person"), List.of("Organization"), List.of());

    @BeforeEach
    void setUp() {
        relationshipRepository = mock(RelationshipRepository.class);
        relationshipTypeService = mock(RelationshipTypeService.class);
        entityService = mock(EntityService.class);
        schemaValidator = mock(SchemaValidator.class);
        relationshipService =
                new RelationshipService(relationshipRepository, relationshipTypeService, entityService, schemaValidator);
    }

    @Test
    void createValidatesTypeAndBothEntitiesBeforeSaving() {
        when(relationshipTypeService.getByName("WORKS_AT")).thenReturn(worksAt);
        when(entityService.getById("ada")).thenReturn(new Entity("ada", "Person", Map.of()));
        when(entityService.getById("acme")).thenReturn(new Entity("acme", "Organization", Map.of()));
        Relationship saved = new Relationship("r1", "WORKS_AT", "ada", "acme", Map.of());
        when(relationshipRepository.create(anyString(), eq("WORKS_AT"), eq("ada"), eq("acme"), anyMap()))
                .thenReturn(saved);

        Relationship created = relationshipService.create("WORKS_AT", "ada", "acme", Map.of());

        assertThat(created.getSourceEntityId()).isEqualTo("ada");
        verify(schemaValidator).validateRelationship(eq(worksAt), eq("Person"), eq("Organization"), anyMap());
    }

    @Test
    void createThrowsNotFoundWhenSourceEntityMissing() {
        when(relationshipTypeService.getByName("WORKS_AT")).thenReturn(worksAt);
        when(entityService.getById("missing")).thenThrow(new NotFoundException("Unknown entity 'missing'"));

        assertThatThrownBy(() -> relationshipService.create("WORKS_AT", "missing", "acme", Map.of()))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(relationshipRepository);
    }

    @Test
    void createAllowsSelfRelationship() {
        RelationshipTypeDefinition mentors =
                new RelationshipTypeDefinition("MENTORS", List.of("Person"), List.of("Person"), List.of());
        when(relationshipTypeService.getByName("MENTORS")).thenReturn(mentors);
        Entity ada = new Entity("ada", "Person", Map.of());
        when(entityService.getById("ada")).thenReturn(ada);
        when(relationshipRepository.create(anyString(), eq("MENTORS"), eq("ada"), eq("ada"), anyMap()))
                .thenReturn(new Relationship("r1", "MENTORS", "ada", "ada", Map.of()));

        Relationship created = relationshipService.create("MENTORS", "ada", "ada", Map.of());

        assertThat(created.getSourceEntityId()).isEqualTo(created.getTargetEntityId());
    }

    @Test
    void createRejectsDisallowedSourceType() {
        when(relationshipTypeService.getByName("WORKS_AT")).thenReturn(worksAt);
        when(entityService.getById("acme")).thenReturn(new Entity("acme", "Organization", Map.of()));
        when(entityService.getById("acme2")).thenReturn(new Entity("acme2", "Organization", Map.of()));
        doThrow(new SchemaValidationException("disallowed source type"))
                .when(schemaValidator)
                .validateRelationship(eq(worksAt), eq("Organization"), eq("Organization"), anyMap());

        assertThatThrownBy(() -> relationshipService.create("WORKS_AT", "acme", "acme2", Map.of()))
                .isInstanceOf(SchemaValidationException.class);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        when(relationshipRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.getById("missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesExistingRelationship() {
        when(relationshipRepository.findById("r1"))
                .thenReturn(Optional.of(new Relationship("r1", "WORKS_AT", "ada", "acme", Map.of())));

        relationshipService.delete("r1");

        verify(relationshipRepository).deleteById("r1");
    }

    @Test
    void createBulkReturnsPerItemOutcomes() {
        when(relationshipTypeService.getByName("WORKS_AT")).thenReturn(worksAt);
        when(entityService.getByIds(Set.of("ada", "acme"))).thenReturn(Map.of(
                "ada", new Entity("ada", "Person", Map.of()),
                "acme", new Entity("acme", "Organization", Map.of())));
        when(relationshipRepository.createBatch(anyList()))
                .thenReturn(List.of(
                        new Relationship("r1", "WORKS_AT", "ada", "acme", Map.of()),
                        new Relationship("r2", "WORKS_AT", "ada", "acme", Map.of())));
        doThrow(new IllegalArgumentException("invalid relationship"))
                .when(schemaValidator).validateRelationship(eq(worksAt), eq("Person"), eq("Organization"), eq(Map.of("bad", true)));

        var result = relationshipService.createBulk(List.of(
                new RelationshipCreateRequest("WORKS_AT", "ada", "acme", Map.of()),
                new RelationshipCreateRequest("WORKS_AT", "ada", "acme", Map.of()),
                new RelationshipCreateRequest("WORKS_AT", "ada", "acme", Map.of("bad", true))));

        assertThat(result.results()).extracting("status")
                .containsExactly("created", "created", "rejected");
        assertThat(result.results().get(2).error()).isEqualTo("invalid relationship");
        verify(entityService).getByIds(Set.of("ada", "acme"));
        verify(relationshipTypeService).getByName("WORKS_AT");
        verify(relationshipRepository).createBatch(argThat(rows -> rows.size() == 2));
    }

    @Test
    void createBulkFallsBackToIndividualWritesToIsolateDatabaseFailure() {
        when(relationshipTypeService.getByName("WORKS_AT")).thenReturn(worksAt);
        when(entityService.getByIds(Set.of("ada", "acme"))).thenReturn(Map.of(
                "ada", new Entity("ada", "Person", Map.of()),
                "acme", new Entity("acme", "Organization", Map.of())));
        when(relationshipRepository.createBatch(anyList()))
                .thenThrow(new IllegalStateException("batch failed"))
                .thenReturn(List.of(new Relationship("r1", "WORKS_AT", "ada", "acme", Map.of())))
                .thenThrow(new IllegalStateException("bad row"));

        var result = relationshipService.createBulk(List.of(
                new RelationshipCreateRequest("WORKS_AT", "ada", "acme", Map.of()),
                new RelationshipCreateRequest("WORKS_AT", "ada", "acme", Map.of())));

        assertThat(result.results()).extracting("status").containsExactly("created", "rejected");
        assertThat(result.results().get(1).error()).isEqualTo("bad row");
    }
}
