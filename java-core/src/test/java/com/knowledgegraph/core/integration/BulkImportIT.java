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
    void bulkEndpointsRequireApiKey() {
        var request = new BulkEntityCreateRequest(List.of());
        ResponseEntity<String> response = restTemplate.postForEntity("/entities/bulk", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
