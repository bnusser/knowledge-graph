package com.knowledgegraph.core.relationship;

import com.knowledgegraph.core.bulk.BulkItemResult;
import com.knowledgegraph.core.bulk.BulkResult;
import com.knowledgegraph.core.entity.Entity;
import com.knowledgegraph.core.entity.EntityService;
import com.knowledgegraph.core.exception.NotFoundException;
import com.knowledgegraph.core.schema.RelationshipTypeDefinition;
import com.knowledgegraph.core.schema.RelationshipTypeService;
import com.knowledgegraph.core.schema.SchemaValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RelationshipService {

    /** See EntityService.WRITE_SUB_BATCH_SIZE for rationale. */
    private static final int WRITE_SUB_BATCH_SIZE = 50;

    private final RelationshipRepository relationshipRepository;
    private final RelationshipTypeService relationshipTypeService;
    private final EntityService entityService;
    private final SchemaValidator schemaValidator;

    public RelationshipService(
            RelationshipRepository relationshipRepository,
            RelationshipTypeService relationshipTypeService,
            EntityService entityService,
            SchemaValidator schemaValidator) {
        this.relationshipRepository = relationshipRepository;
        this.relationshipTypeService = relationshipTypeService;
        this.entityService = entityService;
        this.schemaValidator = schemaValidator;
    }

    public Relationship create(String type, String sourceEntityId, String targetEntityId, Map<String, Object> properties) {
        RelationshipTypeDefinition typeDefinition = relationshipTypeService.getByName(type); // 404 (FR-018)
        Entity source = entityService.getById(sourceEntityId); // 404 (FR-010)
        Entity target = entityService.getById(targetEntityId); // 404 (FR-010); source == target is allowed
        schemaValidator.validateRelationship(typeDefinition, source.getType(), target.getType(), properties); // 422 (FR-013)
        return relationshipRepository.create(
                UUID.randomUUID().toString(), type, sourceEntityId, targetEntityId, properties);
    }

    public BulkResult createBulk(List<RelationshipCreateRequest> requests) {
        List<BulkItemResult> results = new ArrayList<>(Collections.nCopies(requests.size(), null));
        List<PreparedRelationship> toPersist = new ArrayList<>();

        for (int index = 0; index < requests.size(); index++) {
            RelationshipCreateRequest request = requests.get(index);
            try {
                RelationshipTypeDefinition typeDefinition = relationshipTypeService.getByName(request.type()); // 404
                Entity source = entityService.getById(request.sourceEntityId()); // 404
                Entity target = entityService.getById(request.targetEntityId()); // 404
                schemaValidator.validateRelationship(typeDefinition, source.getType(), target.getType(), request.properties()); // 422
                String id = UUID.randomUUID().toString();
                Map<String, Object> row = new HashMap<>();
                row.put("id", id);
                row.put("type", request.type());
                row.put("sourceId", request.sourceEntityId());
                row.put("targetId", request.targetEntityId());
                row.put("properties", request.properties());
                toPersist.add(new PreparedRelationship(index, row));
            } catch (RuntimeException exception) {
                results.set(index, BulkItemResult.rejected(index, errorMessage(exception)));
            }
        }

        for (int start = 0; start < toPersist.size(); start += WRITE_SUB_BATCH_SIZE) {
            List<PreparedRelationship> subBatch =
                    toPersist.subList(start, Math.min(start + WRITE_SUB_BATCH_SIZE, toPersist.size()));
            try {
                List<Map<String, Object>> rows = subBatch.stream().map(PreparedRelationship::row).toList();
                List<Relationship> created = relationshipRepository.createBatch(rows); // one transaction (FR-012)
                for (int i = 0; i < subBatch.size(); i++) {
                    PreparedRelationship prepared = subBatch.get(i);
                    results.set(prepared.index(), BulkItemResult.created(prepared.index(), created.get(i).getId()));
                }
            } catch (RuntimeException exception) {
                String message = errorMessage(exception);
                for (PreparedRelationship prepared : subBatch) {
                    results.set(prepared.index(), BulkItemResult.rejected(prepared.index(), message));
                }
            }
        }
        return new BulkResult(results);
    }

    private record PreparedRelationship(int index, Map<String, Object> row) {}

    public Relationship getById(String id) {
        return relationshipRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Unknown relationship '" + id + "'"));
    }

    public List<Relationship> listByEntity(String entityId, String direction) {
        entityService.getById(entityId); // 404 if the entity itself doesn't exist
        return relationshipRepository.findByEntity(entityId, direction);
    }

    public Relationship update(String id, Map<String, Object> propertyUpdates) {
        Relationship existing = getById(id);
        RelationshipTypeDefinition typeDefinition = relationshipTypeService.getByName(existing.getType());
        Map<String, Object> merged = new java.util.HashMap<>(existing.getProperties());
        merged.putAll(propertyUpdates);
        Entity source = entityService.getById(existing.getSourceEntityId());
        Entity target = entityService.getById(existing.getTargetEntityId());
        schemaValidator.validateRelationship(typeDefinition, source.getType(), target.getType(), merged); // 422
        return relationshipRepository
                .updateProperties(id, merged)
                .orElseThrow(() -> new NotFoundException("Unknown relationship '" + id + "'"));
    }

    public void delete(String id) {
        getById(id); // 404 if unknown
        relationshipRepository.deleteById(id);
    }

    private String errorMessage(RuntimeException exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }
}
