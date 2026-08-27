package com.knowledgegraph.core.schema;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RelationshipTypeDefinitionRepository extends Neo4jRepository<RelationshipTypeDefinition, String> {
}
