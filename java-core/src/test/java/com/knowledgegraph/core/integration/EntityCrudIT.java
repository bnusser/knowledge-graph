package com.knowledgegraph.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledgegraph.core.entity.EntityCreateRequest;
import com.knowledgegraph.core.entity.EntityDto;
import com.knowledgegraph.core.entity.EntityUpdateRequest;
import com.knowledgegraph.core.schema.EntityTypeDefinitionDto;
import com.knowledgegraph.core.schema.PropertyDataType;
import com.knowledgegraph.core.schema.PropertyDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class EntityCrudIT extends AbstractNeo4jIntegrationTest {

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", API_KEY);
        return headers;
    }

    private void registerPersonType() {
        var request = new EntityTypeDefinitionDto(
                "Person", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "name");
        restTemplate.postForEntity("/entity-types", new HttpEntity<>(request, authHeaders()), EntityTypeDefinitionDto.class);
    }

    @Test
    void supportsFullEntityLifecycle() {
        registerPersonType();

        var createRequest = new EntityCreateRequest("Person", Map.of("name", "Ada"));
        ResponseEntity<EntityDto> createResponse =
                restTemplate.postForEntity("/entities", new HttpEntity<>(createRequest, authHeaders()), EntityDto.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = createResponse.getBody().id();

        ResponseEntity<EntityDto> getResponse =
                restTemplate.exchange("/entities/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), EntityDto.class);
        assertThat(getResponse.getBody().properties()).containsEntry("name", "Ada");

        var updateRequest = new EntityUpdateRequest(Map.of("age", 31));
        ResponseEntity<EntityDto> updateResponse = restTemplate.exchange(
                "/entities/" + id, HttpMethod.PATCH, new HttpEntity<>(updateRequest, authHeaders()), EntityDto.class);
        assertThat(updateResponse.getBody().properties()).containsEntry("age", 31);

        ResponseEntity<Void> deleteResponse =
                restTemplate.exchange("/entities/" + id, HttpMethod.DELETE, new HttpEntity<>(authHeaders()), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> afterDelete =
                restTemplate.exchange("/entities/" + id, HttpMethod.GET, new HttpEntity<>(authHeaders()), String.class);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
