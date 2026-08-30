package com.knowledgegraph.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.knowledgegraph.core.bulk.BulkEntityCreateRequest;
import com.knowledgegraph.core.bulk.BulkRelationshipCreateRequest;
import com.knowledgegraph.core.entity.EntityCreateRequest;
import com.knowledgegraph.core.relationship.RelationshipCreateRequest;
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

class BulkImportIT extends AbstractNeo4jIntegrationTest {

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", API_KEY);
        return headers;
    }

    @Test
    void processesValidItemsWhenAnotherItemIsRejected() {
        var person = new EntityTypeDefinitionDto(
                "BulkPerson", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "name");
        restTemplate.postForEntity("/entity-types", new HttpEntity<>(person, authHeaders()), String.class);

        var entityRequest = new BulkEntityCreateRequest(List.of(
                new EntityCreateRequest("BulkPerson", Map.of("name", "Ada")),
                new EntityCreateRequest("MissingType", Map.of("name", "Grace")),
                new EntityCreateRequest("BulkPerson", Map.of("name", "Linus"))));
        ResponseEntity<JsonNode> entityResponse = restTemplate.postForEntity(
                "/entities/bulk", new HttpEntity<>(entityRequest, authHeaders()), JsonNode.class);

        assertThat(entityResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entityResponse.getBody().get("results")).hasSize(3);
        assertThat(entityResponse.getBody().get("results").get(0).get("status").asText()).isEqualTo("created");
        assertThat(entityResponse.getBody().get("results").get(1).get("status").asText()).isEqualTo("rejected");
        assertThat(entityResponse.getBody().get("results").get(2).get("status").asText()).isEqualTo("created");

        var relationshipType = new RelationshipTypeDefinitionDto(
                "KNOWS_BULK", List.of("BulkPerson"), List.of("BulkPerson"), List.of());
        restTemplate.postForEntity("/relationship-types", new HttpEntity<>(relationshipType, authHeaders()), String.class);
        ResponseEntity<JsonNode> entitiesResponse = restTemplate.exchange(
                "/entities?type=BulkPerson", HttpMethod.GET, new HttpEntity<Void>(null, authHeaders()), JsonNode.class);
        assertThat(entitiesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode entities = entitiesResponse.getBody();
        String firstId = entities.get(0).get("id").asText();
        String secondId = entities.get(1).get("id").asText();

        var relationshipRequest = new BulkRelationshipCreateRequest(List.of(
                new RelationshipCreateRequest("KNOWS_BULK", firstId, secondId, Map.of()),
                new RelationshipCreateRequest("MissingRelationship", firstId, secondId, Map.of())));
        ResponseEntity<JsonNode> relationshipResponse = restTemplate.postForEntity(
                "/relationships/bulk", new HttpEntity<>(relationshipRequest, authHeaders()), JsonNode.class);

        assertThat(relationshipResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(relationshipResponse.getBody().get("results").get(0).get("status").asText()).isEqualTo("created");
        assertThat(relationshipResponse.getBody().get("results").get(1).get("status").asText()).isEqualTo("rejected");
    }

    @Test
    void createsEntitiesAndRelationshipsAcrossMultipleWriteSubBatches() {
        // WRITE_SUB_BATCH_SIZE is 50; use 120 items to span three sub-batches (50/50/20) and
        // confirm no items are lost, duplicated, or misindexed at a sub-batch boundary.
        var personType = new EntityTypeDefinitionDto(
                "ScaleTestPerson", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "name");
        restTemplate.postForEntity("/entity-types", new HttpEntity<>(personType, authHeaders()), String.class);

        var entityRequest = new BulkEntityCreateRequest(
                java.util.stream.IntStream.range(0, 120)
                        .mapToObj(i -> new EntityCreateRequest("ScaleTestPerson", Map.of("name", "person-" + i)))
                        .toList());
        ResponseEntity<JsonNode> entityResponse = restTemplate.postForEntity(
                "/entities/bulk", new HttpEntity<>(entityRequest, authHeaders()), JsonNode.class);

        assertThat(entityResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode entityResults = entityResponse.getBody().get("results");
        assertThat(entityResults).hasSize(120);
        for (int i = 0; i < 120; i++) {
            assertThat(entityResults.get(i).get("status").asText()).isEqualTo("created");
            assertThat(entityResults.get(i).get("id").asText()).isNotBlank();
        }
        assertThat(new java.util.HashSet<>(entityResults.findValuesAsText("id"))).hasSize(120); // no duplicate ids

        var relationshipType = new RelationshipTypeDefinitionDto(
                "SCALE_KNOWS", List.of("ScaleTestPerson"), List.of("ScaleTestPerson"), List.of());
        restTemplate.postForEntity("/relationship-types", new HttpEntity<>(relationshipType, authHeaders()), String.class);

        List<String> ids = entityResults.findValuesAsText("id");
        var relationshipRequest = new BulkRelationshipCreateRequest(
                java.util.stream.IntStream.range(0, 120)
                        .mapToObj(i -> new RelationshipCreateRequest("SCALE_KNOWS", ids.get(i), ids.get((i + 1) % 120), Map.of()))
                        .toList());
        ResponseEntity<JsonNode> relationshipResponse = restTemplate.postForEntity(
                "/relationships/bulk", new HttpEntity<>(relationshipRequest, authHeaders()), JsonNode.class);

        assertThat(relationshipResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode relationshipResults = relationshipResponse.getBody().get("results");
        assertThat(relationshipResults).hasSize(120);
        for (int i = 0; i < 120; i++) {
            assertThat(relationshipResults.get(i).get("status").asText()).isEqualTo("created");
        }
    }

    @Test
    void bulkEndpointsRequireApiKey() {
        var request = new BulkEntityCreateRequest(List.of());
        ResponseEntity<String> response = restTemplate.postForEntity("/entities/bulk", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
