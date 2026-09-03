package com.knowledgegraph.core.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.knowledgegraph.core.exception.InvalidTraversalRequestException;
import com.knowledgegraph.core.schema.EntityTypeDefinitionRepository;
import com.knowledgegraph.core.schema.RelationshipTypeDefinitionRepository;
import com.knowledgegraph.core.schema.RelationshipTypeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelationshipTypeServiceTest {

    private RelationshipTypeDefinitionRepository repository;
    private RelationshipTypeService service;

    @BeforeEach
    void setUp() {
        repository = mock(RelationshipTypeDefinitionRepository.class);
        service = new RelationshipTypeService(repository, mock(EntityTypeDefinitionRepository.class));
    }

    @Test
    void emptyOrBlankFilterMeansAllTypes() {
        assertThat(service.normalizeAndValidateFilter(null)).isEmpty();
        assertThat(service.normalizeAndValidateFilter(List.of())).isEmpty();
        assertThat(service.normalizeAndValidateFilter(List.of("", "  "))).isEmpty();
    }

    @Test
    void removesDuplicatesAndSortsDefinedTypes() {
        when(repository.existsById("ALPHA")).thenReturn(true);
        when(repository.existsById("BETA")).thenReturn(true);

        assertThat(service.normalizeAndValidateFilter(List.of(" BETA ", "ALPHA", "BETA")))
                .containsExactly("ALPHA", "BETA");
    }

    @Test
    void acceptsDefinedTypeWithoutRelationshipInstances() {
        when(repository.existsById("EMPTY_DEFINED_TYPE")).thenReturn(true);

        assertThat(service.normalizeAndValidateFilter(List.of("EMPTY_DEFINED_TYPE")))
                .containsExactly("EMPTY_DEFINED_TYPE");
    }

    @Test
    void rejectsUndefinedType() {
        when(repository.existsById("UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> service.normalizeAndValidateFilter(List.of("UNKNOWN")))
                .isInstanceOf(InvalidTraversalRequestException.class)
                .hasMessageContaining("UNKNOWN");
    }
}
