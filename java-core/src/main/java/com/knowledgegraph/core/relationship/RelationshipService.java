package com.knowledgegraph.core.relationship;

import com.knowledgegraph.core.bulk.BulkItemResult;
import com.knowledgegraph.core.bulk.BulkResult;
import com.knowledgegraph.core.entity.Entity;
import com.knowledgegraph.core.entity.EntityService;
import com.knowledgegraph.core.exception.NotFoundException;
import com.knowledgegraph.core.schema.RelationshipTypeDefinition;
import com.knowledgegraph.core.schema.RelationshipTypeService;
import com.knowledgegraph.core.schema.SchemaValidator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RelationshipService {

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
        List<BulkItemResult> results = new java.util.ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            RelationshipCreateRequest request = requests.get(index);
            try {
                Relationship relationship = create(
                        request.type(), request.sourceEntityId(), request.targetEntityId(), request.properties());
                results.add(BulkItemResult.created(index, relationship.getId()));
            } catch (RuntimeException exception) {
                results.add(BulkItemResult.rejected(index, errorMessage(exception)));
            }
        }
        return new BulkResult(results);
    }

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
