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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Covers all three User Story 3 acceptance scenarios (schema definition + enforcement). */
class SchemaEnforcementIT extends AbstractNeo4jIntegrationTest {

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", API_KEY);
        return headers;
    }

    private void registerPersonAndOrganizationTypes() {
        restTemplate.postForEntity(
                "/entity-types",
                new HttpEntity<>(
                        new EntityTypeDefinitionDto(
                                "Person",
                                List.of(
                                        new PropertyDefinition("name", PropertyDataType.STRING, true),
                                        new PropertyDefinition("age", PropertyDataType.INTEGER, false)),
                                "name"),
                        authHeaders()),
                EntityTypeDefinitionDto.class);
        restTemplate.postForEntity(
                "/entity-types",
                new HttpEntity<>(
                        new EntityTypeDefinitionDto(
                                "Organization", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "name"),
                        authHeaders()),
                EntityTypeDefinitionDto.class);
    }

    @Test
    void acceptanceScenario1_conformingEntityIsAcceptedAfterSchemaDefinition() {
        registerPersonAndOrganizationTypes();

        ResponseEntity<EntityDto> response = restTemplate.postForEntity(
                "/entities",
                new HttpEntity<>(new EntityCreateRequest("Person", Map.of("name", "Ada", "age", 30)), authHeaders()),
                EntityDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void acceptanceScenario2_relationshipBetweenDisallowedTypesIsRejected() {
        registerPersonAndOrganizationTypes();
        restTemplate.postForEntity(
                "/relationship-types",
                new HttpEntity<>(
                        new RelationshipTypeDefinitionDto("WORKS_AT", List.of("Person"), List.of("Organization"), List.of()),
                        authHeaders()),
                RelationshipTypeDefinitionDto.class);
        String org1 = restTemplate
                .postForEntity(
                        "/entities",
                        new HttpEntity<>(new EntityCreateRequest("Organization", Map.of("name", "Acme")), authHeaders()),
                        EntityDto.class)
                .getBody()
                .id();
        String org2 = restTemplate
                .postForEntity(
                        "/entities",
                        new HttpEntity<>(new EntityCreateRequest("Organization", Map.of("name", "Globex")), authHeaders()),
                        EntityDto.class)
                .getBody()
                .id();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/relationships",
                new HttpEntity<>(new RelationshipCreateRequest("WORKS_AT", org1, org2, Map.of()), authHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void acceptanceScenario3_missingRequiredPropertyIsRejected() {
        registerPersonAndOrganizationTypes();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/entities",
                new HttpEntity<>(new EntityCreateRequest("Person", Map.of("age", 30)), authHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("name");
    }

    @Test
    void rejectsDuplicateEntityByIdentifyingProperty() {
        registerPersonAndOrganizationTypes();
        restTemplate.postForEntity(
                "/entities",
                new HttpEntity<>(new EntityCreateRequest("Person", Map.of("name", "Ada")), authHeaders()),
                EntityDto.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/entities",
                new HttpEntity<>(new EntityCreateRequest("Person", Map.of("name", "Ada")), authHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsRedefiningAnExistingEntityTypeName() {
        registerPersonAndOrganizationTypes();

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/entity-types",
                new HttpEntity<>(
                        new EntityTypeDefinitionDto(
                                "Person", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "name"),
                        authHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT); // FR-017
    }

    @Test
    void rejectsCreatingEntityOfUnknownType() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/entities",
                new HttpEntity<>(new EntityCreateRequest("Ghost", Map.of()), authHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND); // FR-018
    }
}
