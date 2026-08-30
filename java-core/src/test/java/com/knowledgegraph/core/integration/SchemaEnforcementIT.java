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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Covers all three User Story 3 acceptance scenarios (schema definition + enforcement). */
class SchemaEnforcementIT extends AbstractNeo4jIntegrationTest {

    @Autowired
    private Neo4jClient neo4jClient;

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

    @Test
    void uniquenessConstraintTargetsTheFlatIndexedIdentifyingValueField() {
        registerPersonAndOrganizationTypes();

        // Every entity type's constraint is structurally (Entity.type, Entity.identifyingValue)
        // IS UNIQUE, so Neo4j's IF NOT EXISTS treats the first one created (regardless of the
        // name assigned) as satisfying all later, equivalent constraint-creation attempts — only
        // one such constraint need exist for this assertion to hold, under any name.
        boolean hasEquivalentConstraint = neo4jClient
                .query("SHOW CONSTRAINTS")
                .fetch()
                .all()
                .stream()
                .anyMatch(row -> List.of("type", "identifyingValue").equals(row.get("properties")));

        // Must match what EntityRepository#findByTypeAndIdentifyingValue actually queries —
        // not the fabricated "properties.<name>" key — or duplicate checks degrade to a full
        // label scan as the graph grows (see EntityTypeService#createUniquenessConstraint).
        assertThat(hasEquivalentConstraint).isTrue();
    }

    @Test
    void entityIdConstraintExistsForFastRelationshipCreationLookups() {
        // Without this, MATCH (e:Entity {id: $id}) — used by every relationship create — would
        // degrade to a full label scan across all Entity nodes as the graph grows.
        boolean hasEntityIdConstraint = neo4jClient
                .query("SHOW CONSTRAINTS")
                .fetch()
                .all()
                .stream()
                .anyMatch(row -> List.of("id").equals(row.get("properties")));

        assertThat(hasEntityIdConstraint).isTrue();
    }
}
