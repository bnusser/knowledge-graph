package com.knowledgegraph.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledgegraph.core.entity.EntityCreateRequest;
import com.knowledgegraph.core.entity.EntityDto;
import com.knowledgegraph.core.relationship.RelationshipCreateRequest;
import com.knowledgegraph.core.relationship.RelationshipDto;
import com.knowledgegraph.core.schema.EntityTypeDefinitionDto;
import com.knowledgegraph.core.schema.PropertyDataType;
import com.knowledgegraph.core.schema.PropertyDefinition;
import com.knowledgegraph.core.schema.RelationshipTypeDefinitionDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** FR-005 / SC-004: deletion is blocked while relationships exist, succeeds with cascade, and never leaves an orphan. */
class EntityDeleteWithRelationshipsIT extends AbstractNeo4jIntegrationTest {

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", API_KEY);
        return headers;
    }

    private String createEntity(String type, Map<String, Object> properties) {
        var response = restTemplate.postForEntity(
                "/entities", new HttpEntity<>(new EntityCreateRequest(type, properties), authHeaders()), EntityDto.class);
        return response.getBody().id();
    }

    @Test
    void blocksDeleteThenSucceedsWithCascadeAndLeavesNoOrphan() {
        restTemplate.postForEntity(
                "/entity-types",
                new HttpEntity<>(
                        new EntityTypeDefinitionDto(
                                "Person", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "name"),
                        authHeaders()),
                EntityTypeDefinitionDto.class);
        restTemplate.postForEntity(
                "/relationship-types",
                new HttpEntity<>(
                        new RelationshipTypeDefinitionDto("MENTORS", List.of("Person"), List.of("Person"), List.of()),
                        authHeaders()),
                RelationshipTypeDefinitionDto.class);
        String adaId = createEntity("Person", Map.of("name", "Ada"));
        String bobId = createEntity("Person", Map.of("name", "Bob"));
        restTemplate.postForEntity(
                "/relationships",
                new HttpEntity<>(new RelationshipCreateRequest("MENTORS", adaId, bobId, Map.of()), authHeaders()),
                RelationshipDto.class);

        ResponseEntity<String> blocked = restTemplate.exchange(
                "/entities/" + adaId, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Void> cascaded = restTemplate.exchange(
                "/entities/" + adaId + "?cascade=true", HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        assertThat(cascaded.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<RelationshipDto[]> remaining = restTemplate.exchange(
                "/entities/" + bobId + "/relationships",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                RelationshipDto[].class);
        assertThat(remaining.getBody()).isEmpty(); // no orphaned relationship referencing the deleted entity
    }
}
