package com.knowledgegraph.core.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

/**
 * Ensures the Entity.id uniqueness constraint exists at startup. Without it, every
 * MATCH (e:Entity {id: $id}) — including RelationshipRepository's bulk create, which runs this
 * once per relationship in a batch — degrades to a full label scan across every Entity node,
 * since Spring Data Neo4j does not auto-create an index for application-assigned String @Id
 * fields.
 */
@Component
public class SchemaInitializer implements ApplicationRunner {

    private final Neo4jClient neo4jClient;

    public SchemaInitializer(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        neo4jClient
                .query("CREATE CONSTRAINT uniq_entity_id IF NOT EXISTS FOR (e:Entity) REQUIRE e.id IS UNIQUE")
                .run();
    }
}
