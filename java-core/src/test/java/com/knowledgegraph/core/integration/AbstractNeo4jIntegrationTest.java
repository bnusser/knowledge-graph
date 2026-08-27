package com.knowledgegraph.core.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Neo4jContainer;

/**
 * Shared Testcontainers Neo4j instance + API key for all integration tests. Uses Testcontainers'
 * "singleton container" pattern (a static instance started once, never explicitly stopped —
 * Ryuk reaps it when the whole test JVM exits) instead of {@code @Container}/{@code @Testcontainers},
 * because the latter stops the container after each test *class*, which broke every IT class
 * after the first when they all extend this same base and share one static field.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractNeo4jIntegrationTest {

    protected static final String API_KEY = "test-api-key";

    protected static final Neo4jContainer<?> NEO4J =
            new Neo4jContainer<>("neo4j:5.24").withAdminPassword("test-password");

    static {
        NEO4J.start();
    }

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "test-password");
        registry.add("app.security.api-key", () -> API_KEY);
    }

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    protected TestRestTemplate restTemplate;

    /** Boot's default request factory can't send PATCH; swap in the JDK 11+ HttpClient-based one. */
    @BeforeEach
    void useJdkHttpClientForPatchSupport() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    /** Clears the whole graph between tests so each test starts from a clean slate. */
    @AfterEach
    void clearDatabase() {
        neo4jClient.query("MATCH (n) DETACH DELETE n").run();
    }
}
