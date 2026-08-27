package com.knowledgegraph.core.entity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EntityRepository extends Neo4jRepository<Entity, String> {

    List<Entity> findByType(String type);

    /** Duplicate detection (FR-014) via the flat, natively-indexable {@code identifyingValue} field. */
    Optional<Entity> findByTypeAndIdentifyingValue(String type, String identifyingValue);
}
