package com.knowledgegraph.core.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

/** Opt-in end-to-end benchmark for an already loaded PaySim or Elliptic stack. */
@Tag("perf")
class TraversalDatasetPerformanceIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void validatesFullDatasetLatencyStructureAndReadOnlyBehavior() throws Exception {
        assumeTrue(Boolean.getBoolean("traversal.perf.enabled"),
                "Set -Dtraversal.perf.enabled=true to run against an external loaded graph");

        String dataset = required("traversal.perf.dataset").toLowerCase(Locale.ROOT);
        assertThat(dataset).isIn("paysim", "elliptic");
        String apiUrl = stripTrailingSlash(required("traversal.perf.apiUrl"));
        String apiKey = required("traversal.perf.apiKey");
        String neo4jUri = requiredEnvironment("TRAVERSAL_PERF_NEO4J_URI");
        String neo4jUsername = requiredEnvironment("TRAVERSAL_PERF_NEO4J_USERNAME");
        String neo4jPassword = requiredEnvironment("TRAVERSAL_PERF_NEO4J_PASSWORD");
        Path manifestPath = Path.of(required("traversal.perf.seedManifest"));
        List<Seed> seeds = readSeeds(manifestPath);
        int measuredRequests = Math.max(200, Integer.getInteger("traversal.perf.requests", 240));
        int warmups = Math.max(10, Integer.getInteger("traversal.perf.warmups", 24));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        try (Driver driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUsername, neo4jPassword))) {
            Counts before = counts(driver);
            List<String> availableTypes = relationshipTypes(driver);
            assertThat(availableTypes).as("loaded graph relationship types").isNotEmpty();

            for (int index = 0; index < warmups; index++) {
                execute(client, apiUrl, apiKey, requestPath(seeds, availableTypes, index));
            }

            List<Long> latencies = new ArrayList<>(measuredRequests);
            for (int index = 0; index < measuredRequests; index++) {
                String path = requestPath(seeds, availableTypes, index);
                long started = System.nanoTime();
                JsonNode result = execute(client, apiUrl, apiKey, path);
                latencies.add((System.nanoTime() - started) / 1_000_000);
                assertNeighborhoodStructure(result);
            }

            Counts after = counts(driver);
            assertThat(after).as("traversal must be read-only").isEqualTo(before);
            Collections.sort(latencies);
            long belowTwoSeconds = latencies.stream().filter(value -> value < 2_000).count();
            double underTwoSecondPercent = belowTwoSeconds * 100.0 / latencies.size();
            String report = String.format(
                    Locale.ROOT,
                    "TRAVERSAL_PERF dataset=%s nodes=%d relationships=%d seeds=%d warmups=%d requests=%d "
                            + "p50Ms=%d p95Ms=%d p99Ms=%d maxMs=%d underTwoSecondsPercent=%.2f "
                            + "workload=hops1-3,directions-outgoing-incoming-both,limits100-500-1000,filtered-alternating "
                            + "java=%s os=%s apiUrl=%s neo4jUri=%s manifest=%s",
                    dataset,
                    before.nodes(),
                    before.relationships(),
                    seeds.size(),
                    warmups,
                    measuredRequests,
                    percentile(latencies, 50),
                    percentile(latencies, 95),
                    percentile(latencies, 99),
                    latencies.getLast(),
                    underTwoSecondPercent,
                    System.getProperty("java.version"),
                    System.getProperty("os.name"),
                    apiUrl,
                    neo4jUri,
                    manifestPath.toAbsolutePath());
            System.out.println(report);
            assertThat(underTwoSecondPercent).isGreaterThanOrEqualTo(95.0);
        }
    }

    private JsonNode execute(HttpClient client, String apiUrl, String apiKey, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + path))
                .header("X-API-Key", apiKey)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(path + " response status").isEqualTo(200);
        return MAPPER.readTree(response.body());
    }

    private String requestPath(List<Seed> seeds, List<String> types, int index) {
        Seed seed = seeds.get(index % seeds.size());
        int maxHops = 1 + index % 3;
        String direction = List.of("outgoing", "incoming", "both").get((index / 3) % 3);
        int limit = List.of(100, 500, 1_000).get((index / 9) % 3);
        StringBuilder path = new StringBuilder("/entities/")
                .append(encode(seed.entityId()))
                .append("/neighborhood?maxHops=")
                .append(maxHops)
                .append("&direction=")
                .append(direction)
                .append("&limit=")
                .append(limit);
        if (index % 2 == 1) {
            String type = seed.relationshipType() != null
                    ? seed.relationshipType()
                    : types.get(index % types.size());
            path.append("&relationshipTypes=").append(encode(type));
        }
        return path.toString();
    }

    private void assertNeighborhoodStructure(JsonNode result) {
        assertThat(result.path("startEntityId").isTextual()).isTrue();
        assertThat(result.path("resultSize").asInt())
                .isEqualTo(result.path("entities").size() + result.path("relationships").size());
        assertThat(result.path("resultSize").asInt()).isLessThanOrEqualTo(result.path("limit").asInt());
        assertThat(result.path("truncated").isBoolean()).isTrue();
        result.path("entities").forEach(entity -> {
            assertThat(entity.hasNonNull("id")).isTrue();
            assertThat(entity.hasNonNull("type")).isTrue();
            assertThat(entity.path("properties").isObject()).isTrue();
            assertThat(entity.path("distance").canConvertToInt()).isTrue();
        });
        Set<String> entityIds = new java.util.HashSet<>();
        result.path("entities").forEach(entity -> entityIds.add(entity.path("id").asText()));
        result.path("relationships").forEach(relationship -> {
            assertThat(relationship.hasNonNull("id")).isTrue();
            assertThat(relationship.hasNonNull("sourceEntityId")).isTrue();
            assertThat(relationship.hasNonNull("targetEntityId")).isTrue();
            assertThat(relationship.path("properties").isObject()).isTrue();
            assertThat(entityIds).contains(
                    relationship.path("sourceEntityId").asText(),
                    relationship.path("targetEntityId").asText());
        });
    }

    private Counts counts(Driver driver) {
        try (var session = driver.session()) {
            return session.executeRead(transaction -> {
                var record = transaction.run(
                                "MATCH (n:Entity) WITH count(n) AS nodes "
                                        + "OPTIONAL MATCH ()-[r:RELATES]->() RETURN nodes, count(r) AS relationships")
                        .single();
                return new Counts(record.get("nodes").asLong(), record.get("relationships").asLong());
            });
        }
    }

    private List<String> relationshipTypes(Driver driver) {
        try (var session = driver.session()) {
            return session.executeRead(transaction -> transaction
                    .run("MATCH ()-[r:RELATES]->() WHERE r.type IS NOT NULL "
                            + "RETURN DISTINCT r.type AS type ORDER BY type LIMIT 20")
                    .list(record -> record.get("type").asString()));
        }
    }

    private List<Seed> readSeeds(Path path) throws Exception {
        List<Seed> seeds = Files.readAllLines(path).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.split(",", -1))
                .map(parts -> new Seed(
                        parts.length > 1 ? parts[0].trim() : "unspecified",
                        parts.length > 1 ? parts[1].trim() : parts[0].trim(),
                        parts.length > 2 && !parts[2].isBlank() ? parts[2].trim() : null))
                .toList();
        assertThat(seeds).as("seed manifest").isNotEmpty();
        assertThat(seeds).allSatisfy(seed -> {
            assertThat(seed.band()).isNotBlank();
            assertThat(seed.entityId()).isNotBlank();
        });
        Set<String> bands = seeds.stream()
                .map(seed -> seed.band().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        assertThat(bands).as("low/medium/high degree seed coverage")
                .contains("low", "medium", "high");
        return seeds;
    }

    private long percentile(List<Long> sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private String required(String name) {
        String value = System.getProperty(name);
        assertThat(value).as("required system property " + name).isNotBlank();
        return value;
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertThat(value).as("required environment variable " + name).isNotBlank();
        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record Seed(String band, String entityId, String relationshipType) {}

    private record Counts(long nodes, long relationships) {}
}
