package com.knowledgegraph.core.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EntityRepository extends Neo4jRepository<Entity, String> {

    List<Entity> findByType(String type);

    /** Duplicate detection (FR-014) via the flat, natively-indexable {@code identifyingValue} field. */
    Optional<Entity> findByTypeAndIdentifyingValue(String type, String identifyingValue);

    /**
     * Creates many entities in a single UNWIND-based statement — one transaction/commit for the
     * whole batch rather than one per entity — since per-item commit overhead dominates bulk-load
     * cost. Bypasses {@code saveAll}, whose SDN-generated Cypher for this entity shape produces
     * an invalid self-conflicting {@code UNWIND ... MERGE (entity)} query (variable name clash
     * between the UNWIND binding and the MERGE pattern). {@code rows} entries need keys: id,
     * type, propertiesJson, identifyingValue.
     */
    @Query("UNWIND $rows AS row "
            + "CREATE (e:Entity {id: row.id, type: row.type, propertiesJson: row.propertiesJson, identifyingValue: row.identifyingValue})")
    void createBatch(@Param("rows") List<Map<String, Object>> rows);
}
