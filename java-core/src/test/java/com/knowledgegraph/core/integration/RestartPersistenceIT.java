package com.knowledgegraph.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.knowledgegraph.core.KnowledgeGraphCoreApplication;
import com.knowledgegraph.core.entity.EntityCreateRequest;
import com.knowledgegraph.core.entity.EntityDto;
import com.knowledgegraph.core.entity.EntityRepository;
import com.knowledgegraph.core.schema.EntityTypeDefinitionDto;
import com.knowledgegraph.core.schema.PropertyDataType;
import com.knowledgegraph.core.schema.PropertyDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

/**
 * FR-015 / SC-005: data survives an application restart. Simulates a restart by starting a brand
 * new Spring context against the same (still-running) Testcontainers Neo4j instance, rather than
 * reusing the test's managed context, and reading the data back through it.
 */
class RestartPersistenceIT extends AbstractNeo4jIntegrationTest {

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", API_KEY);
        return headers;
    }

    @Test
    void dataSurvivesApplicationRestart() {
        restTemplate.postForEntity(
                "/entity-types",
                new HttpEntity<>(
                        new EntityTypeDefinitionDto(
                                "Person", List.of(new PropertyDefinition("name", PropertyDataType.STRING, true)), "name"),
                        authHeaders()),
                EntityTypeDefinitionDto.class);
        var created = restTemplate.postForEntity(
                "/entities",
                new HttpEntity<>(new EntityCreateRequest("Person", Map.of("name", "Ada")), authHeaders()),
                EntityDto.class);
        String entityId = created.getBody().id();

        // Command-line args (highest precedence) so these actually override application.yml's
        // defaults — SpringApplicationBuilder#properties() sets lowest-precedence "default
        // properties", which application.yml would otherwise win over.
        ConfigurableApplicationContext restarted = new SpringApplicationBuilder(KnowledgeGraphCoreApplication.class)
                .run(
                        "--spring.neo4j.uri=" + NEO4J.getBoltUrl(),
                        "--spring.neo4j.authentication.username=neo4j",
                        "--spring.neo4j.authentication.password=test-password",
                        "--app.security.api-key=" + API_KEY,
                        "--server.port=0");
        try {
            EntityRepository freshRepository = restarted.getBean(EntityRepository.class);
            assertThat(freshRepository.findById(entityId)).isPresent();
            assertThat(freshRepository.findById(entityId).get().getProperties()).containsEntry("name", "Ada");
        } finally {
            restarted.close();
        }
    }
}
