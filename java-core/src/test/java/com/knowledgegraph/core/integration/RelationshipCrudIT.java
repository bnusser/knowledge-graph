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

class RelationshipCrudIT extends AbstractNeo4jIntegrationTest {

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
    void supportsRelationshipCrudAndTraversalFromBothEndpoints() {
        restTemplate.postForEntity(
                "/entity-types",
                new HttpEntity<>(
                        new EntityTypeDefinitionDto(
                                "Person", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "name"),
                        authHeaders()),
                EntityTypeDefinitionDto.class);
        restTemplate.postForEntity(
                "/entity-types",
                new HttpEntity<>(
                        new EntityTypeDefinitionDto(
                                "Organization", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "name"),
                        authHeaders()),
                EntityTypeDefinitionDto.class);
        restTemplate.postForEntity(
                "/relationship-types",
                new HttpEntity<>(
                        new RelationshipTypeDefinitionDto("WORKS_AT", List.of("Person"), List.of("Organization"), List.of()),
                        authHeaders()),
                RelationshipTypeDefinitionDto.class);

        String adaId = createEntity("Person", Map.of("name", "Ada"));
        String acmeId = createEntity("Organization", Map.of("name", "Acme"));

        ResponseEntity<RelationshipDto> createResponse = restTemplate.postForEntity(
                "/relationships",
                new HttpEntity<>(new RelationshipCreateRequest("WORKS_AT", adaId, acmeId, Map.of()), authHeaders()),
                RelationshipDto.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String relationshipId = createResponse.getBody().id();

        ResponseEntity<RelationshipDto[]> outgoing = restTemplate.exchange(
                "/entities/" + adaId + "/relationships?direction=outgoing",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                RelationshipDto[].class);
        assertThat(outgoing.getBody()).hasSize(1);

        ResponseEntity<RelationshipDto[]> incoming = restTemplate.exchange(
                "/entities/" + acmeId + "/relationships?direction=incoming",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                RelationshipDto[].class);
        assertThat(incoming.getBody()).hasSize(1);

        restTemplate.exchange(
                "/relationships/" + relationshipId, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        ResponseEntity<RelationshipDto[]> afterDelete = restTemplate.exchange(
                "/entities/" + adaId + "/relationships",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                RelationshipDto[].class);
        assertThat(afterDelete.getBody()).isEmpty();
    }

    @Test
    void supportsSelfRelationship() {
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

        ResponseEntity<RelationshipDto> response = restTemplate.postForEntity(
                "/relationships",
                new HttpEntity<>(new RelationshipCreateRequest("MENTORS", adaId, adaId, Map.of()), authHeaders()),
                RelationshipDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().sourceEntityId()).isEqualTo(response.getBody().targetEntityId());
    }
}
