package com.knowledgegraph.core.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TraversalIT extends AbstractNeo4jIntegrationTest {

    @Autowired
    private Neo4jClient neo4jClient;

    @BeforeEach
    void seedRepresentativeGraph() {
        neo4jClient
                .query("CREATE (a:Entity {id:'A', type:'Node', propertiesJson:'{\"name\":\"A\"}'}), "
                        + "(b:Entity {id:'B', type:'Node', propertiesJson:'{\"name\":\"B\"}'}), "
                        + "(c:Entity {id:'C', type:'Node', propertiesJson:'{\"name\":\"C\"}'}), "
                        + "(d:Entity {id:'D', type:'Node', propertiesJson:'{\"name\":\"D\"}'}), "
                        + "(z:Entity {id:'Z', type:'Node', propertiesJson:'{}'}), "
                        + "(a)-[:RELATES {id:'r01', type:'ALPHA', weight:1}]->(b), "
                        + "(a)-[:RELATES {id:'r02', type:'BETA', weight:2}]->(c), "
                        + "(b)-[:RELATES {id:'r03', type:'ALPHA'}]->(d), "
                        + "(c)-[:RELATES {id:'r04', type:'ALPHA'}]->(d), "
                        + "(d)-[:RELATES {id:'r05', type:'BETA'}]->(a), "
                        + "(c)-[:RELATES {id:'r06', type:'ALPHA'}]->(c), "
                        + "(a)-[:RELATES {id:'r07', type:'ALPHA'}]->(b), "
                        + "(b)-[:RELATES {id:'r09', type:'BETA'}]->(c), "
                        + "(:RelationshipTypeDefinition {name:'ALPHA', propertiesJson:'[]'}), "
                        + "(:RelationshipTypeDefinition {name:'BETA', propertiesJson:'[]'}), "
                        + "(:RelationshipTypeDefinition {name:'UNUSED', propertiesJson:'[]'})")
                .run();
    }

    @Test
    void returnsBoundedDeterministicCycleSafeNeighborhoodWithoutWriting() {
        long nodesBefore = count("MATCH (n) RETURN count(n) AS count");
        long relationshipsBefore = count("MATCH ()-[r]->() RETURN count(r) AS count");

        JsonNode first = get("/entities/A/neighborhood?maxHops=2&direction=both&limit=100").getBody();
        JsonNode second = get("/entities/A/neighborhood?maxHops=2&direction=both&limit=100").getBody();

        assertThat(first).isEqualTo(second);
        assertThat(values(first.get("entities"), "id")).containsExactly("A", "B", "C", "D");
        assertThat(values(first.get("relationships"), "id"))
                .containsExactly("r01", "r02", "r05", "r07", "r03", "r04", "r06", "r09");
        assertThat(first.get("resultSize").asInt()).isEqualTo(12);
        assertThat(first.get("truncated").asBoolean()).isFalse();
        assertThat(count("MATCH (n) RETURN count(n) AS count")).isEqualTo(nodesBefore);
        assertThat(count("MATCH ()-[r]->() RETURN count(r) AS count")).isEqualTo(relationshipsBefore);
    }

    @Test
    void handlesIsolatedZeroHopAndMissingNeighborhoodStarts() {
        JsonNode isolated = get("/entities/Z/neighborhood?maxHops=3").getBody();
        assertThat(values(isolated.get("entities"), "id")).containsExactly("Z");
        assertThat(isolated.get("relationships")).isEmpty();

        JsonNode zeroHop = get("/entities/A/neighborhood?maxHops=0").getBody();
        assertThat(values(zeroHop.get("entities"), "id")).containsExactly("A");
        assertThat(zeroHop.get("relationships")).isEmpty();
        assertThat(get("/entities/missing/neighborhood").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void findsDeterministicShortestPathsAndReportsValidNoPathOutcomes() {
        JsonNode found = get("/entities/A/shortest-path/D?maxHops=3&direction=outgoing").getBody();
        assertThat(found.get("outcome").asText()).isEqualTo("found");
        assertThat(found.get("hopCount").asInt()).isEqualTo(2);
        assertThat(values(found.get("entities"), "id")).containsExactly("A", "B", "D");
        assertThat(values(found.get("relationships"), "id")).containsExactly("r01", "r03");

        JsonNode excluded = get("/entities/A/shortest-path/D?maxHops=1&direction=outgoing").getBody();
        assertThat(excluded.get("outcome").asText()).isEqualTo("no_path");
        assertThat(excluded.get("hopCount").isNull()).isTrue();
        assertThat(excluded.get("entities")).isEmpty();

        JsonNode same = get("/entities/A/shortest-path/A?maxHops=0").getBody();
        assertThat(same.get("outcome").asText()).isEqualTo("found");
        assertThat(same.get("hopCount").asInt()).isZero();
        assertThat(values(same.get("entities"), "id")).containsExactly("A");
    }

    @Test
    void appliesDirectionsTypesAndDenseAtomicTruncation() {
        JsonNode incoming = get("/entities/A/neighborhood?maxHops=1&direction=incoming&limit=100").getBody();
        assertThat(values(incoming.get("relationships"), "id")).containsExactly("r05");

        JsonNode filtered = get("/entities/A/neighborhood?maxHops=1&direction=outgoing"
                + "&relationshipTypes=BETA&relationshipTypes=ALPHA&relationshipTypes=ALPHA&limit=100").getBody();
        assertThat(textValues(filtered.get("relationshipTypes"))).containsExactly("ALPHA", "BETA");
        assertThat(values(filtered.get("relationships"), "id")).containsExactly("r01", "r02", "r07");

        JsonNode truncated = get("/entities/A/neighborhood?maxHops=1&direction=outgoing&limit=3").getBody();
        assertThat(truncated.get("resultSize").asInt()).isEqualTo(3);
        assertThat(truncated.get("truncated").asBoolean()).isTrue();
        assertThat(values(truncated.get("relationships"), "id")).containsExactly("r01");
    }

    @Test
    void distinguishesMalformedSemanticMissingAndUnauthorizedRequests() {
        assertThat(get("/entities/A/neighborhood?maxHops=word").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/entities/A/neighborhood?maxHops=11").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get("/entities/A/neighborhood?limit=0").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get("/entities/A/neighborhood?relationshipTypes=UNKNOWN").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get("/entities/A/shortest-path/missing").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<JsonNode> unauthorized = restTemplate.exchange(
                "/entities/A/neighborhood", HttpMethod.GET, HttpEntity.EMPTY, JsonNode.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publishesBothTraversalPathsInGeneratedOpenApi() {
        JsonNode document = restTemplate.getForObject("/v3/api-docs", JsonNode.class);
        assertThat(document.at("/info/version").asText()).isEqualTo("0.3.0");
        assertThat(document.at("/paths/~1entities~1{entityId}~1neighborhood/get").isMissingNode()).isFalse();
        assertThat(document.at("/paths/~1entities~1{sourceEntityId}~1shortest-path~1{destinationEntityId}/get")
                        .isMissingNode())
                .isFalse();
        assertThat(textValues(document.at("/components/schemas/NeighborhoodResultDto/required")))
                .containsExactlyInAnyOrder(
                        "startEntityId", "maxHops", "direction", "relationshipTypes", "limit",
                        "resultSize", "truncated", "entities", "relationships");
        assertThat(textValues(document.at("/components/schemas/ShortestPathResultDto/required")))
                .containsExactlyInAnyOrder(
                        "sourceEntityId", "destinationEntityId", "maxHops", "direction",
                        "relationshipTypes", "outcome", "hopCount", "entities", "relationships");
    }

    private ResponseEntity<JsonNode> get(String path) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders()), JsonNode.class);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", API_KEY);
        return headers;
    }

    private List<String> values(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.get(field).asText()));
        return values;
    }

    private List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asText()));
        return values;
    }

    private long count(String query) {
        return neo4jClient.query(query).fetchAs(Long.class)
                .mappedBy((typeSystem, record) -> record.get("count").asLong())
                .one()
                .orElseThrow();
    }
}
