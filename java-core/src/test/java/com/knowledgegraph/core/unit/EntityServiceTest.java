package com.knowledgegraph.core.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.knowledgegraph.core.entity.Entity;
import com.knowledgegraph.core.entity.EntityCreateRequest;
import com.knowledgegraph.core.entity.EntityRepository;
import com.knowledgegraph.core.entity.EntityService;
import com.knowledgegraph.core.exception.ConflictException;
import com.knowledgegraph.core.exception.NotFoundException;
import com.knowledgegraph.core.relationship.RelationshipRepository;
import com.knowledgegraph.core.schema.EntityTypeDefinition;
import com.knowledgegraph.core.schema.EntityTypeService;
import com.knowledgegraph.core.schema.PropertyDataType;
import com.knowledgegraph.core.schema.PropertyDefinition;
import com.knowledgegraph.core.schema.SchemaValidator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EntityServiceTest {

    private EntityRepository entityRepository;
    private RelationshipRepository relationshipRepository;
    private EntityTypeService entityTypeService;
    private SchemaValidator schemaValidator;
    private EntityService entityService;

    private final EntityTypeDefinition personType = new EntityTypeDefinition(
            "Person", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "name");

    @BeforeEach
    void setUp() {
        entityRepository = mock(EntityRepository.class);
        relationshipRepository = mock(RelationshipRepository.class);
        entityTypeService = mock(EntityTypeService.class);
        schemaValidator = mock(SchemaValidator.class);
        entityService = new EntityService(entityRepository, relationshipRepository, entityTypeService, schemaValidator);
    }

    @Test
    void createSavesEntityWhenTypeExistsAndNoDuplicate() {
        when(entityTypeService.getByName("Person")).thenReturn(personType);
        when(entityRepository.findByTypeAndIdentifyingValue(anyString(), anyString())).thenReturn(Optional.empty());
        when(entityRepository.save(any(Entity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Entity created = entityService.create("Person", Map.of("name", "Ada"));

        assertThat(created.getType()).isEqualTo("Person");
        assertThat(created.getProperties()).containsEntry("name", "Ada");
        verify(schemaValidator).validateEntityProperties(eq(personType), any());
    }

    @Test
    void createThrowsNotFoundForUnknownType() {
        when(entityTypeService.getByName("Ghost")).thenThrow(new NotFoundException("Unknown entity type 'Ghost'"));

        assertThatThrownBy(() -> entityService.create("Ghost", Map.of()))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(entityRepository);
    }

    @Test
    void createThrowsConflictForDuplicateIdentifyingProperty() {
        when(entityTypeService.getByName("Person")).thenReturn(personType);
        when(entityRepository.findByTypeAndIdentifyingValue("Person", "Ada"))
                .thenReturn(Optional.of(new Entity("existing-id", "Person", Map.of("name", "Ada"))));

        assertThatThrownBy(() -> entityService.create("Person", Map.of("name", "Ada")))
                .isInstanceOf(ConflictException.class);
        verify(entityRepository, never()).save(any());
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        when(entityRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entityService.getById("missing")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteWithNoRelationshipsDeletesDirectly() {
        Entity entity = new Entity("e1", "Person", Map.of("name", "Ada"));
        when(entityRepository.findById("e1")).thenReturn(Optional.of(entity));
        when(relationshipRepository.countForEntity("e1")).thenReturn(0L);

        entityService.delete("e1", false);

        verify(relationshipRepository, never()).deleteAllForEntity(any());
        verify(entityRepository).deleteById("e1");
    }

    @Test
    void deleteWithRelationshipsAndNoCascadeThrowsConflict() {
        Entity entity = new Entity("e1", "Person", Map.of("name", "Ada"));
        when(entityRepository.findById("e1")).thenReturn(Optional.of(entity));
        when(relationshipRepository.countForEntity("e1")).thenReturn(2L);

        assertThatThrownBy(() -> entityService.delete("e1", false)).isInstanceOf(ConflictException.class);
        verify(entityRepository, never()).deleteById(any());
    }

    @Test
    void deleteWithRelationshipsAndCascadeDeletesRelationshipsThenEntity() {
        Entity entity = new Entity("e1", "Person", Map.of("name", "Ada"));
        when(entityRepository.findById("e1")).thenReturn(Optional.of(entity));
        when(relationshipRepository.countForEntity("e1")).thenReturn(2L);

        entityService.delete("e1", true);

        verify(relationshipRepository).deleteAllForEntity("e1");
        verify(entityRepository).deleteById("e1");
    }

    @Test
    void updateMergesPropertiesAndRevalidates() {
        Entity entity = new Entity("e1", "Person", new java.util.HashMap<>(Map.of("name", "Ada", "age", 30)));
        when(entityRepository.findById("e1")).thenReturn(Optional.of(entity));
        when(entityTypeService.getByName("Person")).thenReturn(personType);
        when(entityRepository.save(any(Entity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Entity updated = entityService.update("e1", Map.of("age", 31));

        assertThat(updated.getProperties()).containsEntry("name", "Ada").containsEntry("age", 31);
    }

        @Test
        void createBulkReturnsPerItemOutcomes() {
        when(entityTypeService.getByName("Person")).thenReturn(personType);
        when(entityRepository.findByTypeAndIdentifyingValue("Person", "Ada"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(new Entity("existing-id", "Person", Map.of("name", "Ada"))));
        when(entityRepository.save(any(Entity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalArgumentException("invalid item"))
            .when(schemaValidator).validateEntityProperties(eq(personType), eq(Map.of("name", "Invalid")));

        var result = entityService.createBulk(List.of(
            new EntityCreateRequest("Person", Map.of("name", "Ada")),
            new EntityCreateRequest("Person", Map.of("name", "Ada")),
            new EntityCreateRequest("Person", Map.of("name", "Invalid"))));

        assertThat(result.results()).extracting("status")
            .containsExactly("created", "already_present", "rejected");
        assertThat(result.results().get(0).index()).isZero();
        assertThat(result.results().get(1).id()).isEqualTo("existing-id");
        assertThat(result.results().get(2).error()).isEqualTo("invalid item");
        }
}
