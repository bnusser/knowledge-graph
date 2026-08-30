package com.knowledgegraph.core.entity;

import com.knowledgegraph.core.bulk.BulkItemResult;
import com.knowledgegraph.core.bulk.BulkResult;
import com.knowledgegraph.core.exception.ConflictException;
import com.knowledgegraph.core.exception.NotFoundException;
import com.knowledgegraph.core.relationship.RelationshipRepository;
import com.knowledgegraph.core.schema.EntityTypeDefinition;
import com.knowledgegraph.core.schema.EntityTypeService;
import com.knowledgegraph.core.schema.SchemaValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EntityService {

    /**
     * Writes are grouped into sub-batches of this size and persisted via a single
     * {@code saveAll} (one transaction/commit) rather than one transaction per item, since
     * per-item transaction commit overhead dominates bulk-load cost (see research notes on the
     * 002-elliptic-paysim-loaders load performance investigation). Kept small enough that one
     * rare write failure only affects this many items, preserving FR-012's spirit at a coarser
     * (sub-batch, not whole-request) grain.
     */
    private static final int WRITE_SUB_BATCH_SIZE = 50;

    private final EntityRepository entityRepository;
    private final RelationshipRepository relationshipRepository;
    private final EntityTypeService entityTypeService;
    private final SchemaValidator schemaValidator;

    public EntityService(
            EntityRepository entityRepository,
            RelationshipRepository relationshipRepository,
            EntityTypeService entityTypeService,
            SchemaValidator schemaValidator) {
        this.entityRepository = entityRepository;
        this.relationshipRepository = relationshipRepository;
        this.entityTypeService = entityTypeService;
        this.schemaValidator = schemaValidator;
    }

    public Entity create(String type, Map<String, Object> properties) {
        EntityTypeDefinition typeDefinition = entityTypeService.getByName(type); // 404 if unknown (FR-018)
        schemaValidator.validateEntityProperties(typeDefinition, properties); // 422 on violation (FR-013)
        checkDuplicate(typeDefinition, properties); // 409 on duplicate (FR-014)
        Entity entity = new Entity(UUID.randomUUID().toString(), type, properties);
        entity.setIdentifyingValue(identifyingValueOf(typeDefinition, properties));
        return entityRepository.save(entity);
    }

    public BulkResult createBulk(List<EntityCreateRequest> requests) {
        List<BulkItemResult> results = new ArrayList<>(Collections.nCopies(requests.size(), null));
        List<PreparedEntity> toPersist = new ArrayList<>();

        for (int index = 0; index < requests.size(); index++) {
            EntityCreateRequest request = requests.get(index);
            try {
                EntityTypeDefinition typeDefinition = entityTypeService.getByName(request.type()); // 404 (FR-018)
                schemaValidator.validateEntityProperties(typeDefinition, request.properties()); // 422 (FR-013)
                String identifyingValue = identifyingValueOf(typeDefinition, request.properties());
                if (identifyingValue != null) {
                    var existing = entityRepository.findByTypeAndIdentifyingValue(request.type(), identifyingValue);
                    if (existing.isPresent()) {
                        results.set(index, BulkItemResult.alreadyPresent(index, existing.get().getId()));
                        continue;
                    }
                }
                Entity entity = new Entity(UUID.randomUUID().toString(), request.type(), request.properties());
                entity.setIdentifyingValue(identifyingValue);
                toPersist.add(new PreparedEntity(index, entity));
            } catch (RuntimeException exception) {
                results.set(index, BulkItemResult.rejected(index, errorMessage(exception)));
            }
        }

        for (int start = 0; start < toPersist.size(); start += WRITE_SUB_BATCH_SIZE) {
            List<PreparedEntity> subBatch = toPersist.subList(start, Math.min(start + WRITE_SUB_BATCH_SIZE, toPersist.size()));
            try {
                List<Map<String, Object>> rows = subBatch.stream().map(this::toRow).toList();
                entityRepository.createBatch(rows); // one transaction (FR-012)
                for (PreparedEntity prepared : subBatch) {
                    results.set(prepared.index(), BulkItemResult.created(prepared.index(), prepared.entity().getId()));
                }
            } catch (RuntimeException exception) {
                String message = errorMessage(exception);
                for (PreparedEntity prepared : subBatch) {
                    results.set(prepared.index(), BulkItemResult.rejected(prepared.index(), message));
                }
            }
        }
        return new BulkResult(results);
    }

    private Map<String, Object> toRow(PreparedEntity prepared) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", prepared.entity().getId());
        row.put("type", prepared.entity().getType());
        row.put("propertiesJson", prepared.entity().getPropertiesJson());
        row.put("identifyingValue", prepared.entity().getIdentifyingValue());
        return row;
    }

    private record PreparedEntity(int index, Entity entity) {}

    public Entity getById(String id) {
        return entityRepository.findById(id).orElseThrow(() -> new NotFoundException("Unknown entity '" + id + "'"));
    }

    public List<Entity> list(String type) {
        return type != null ? entityRepository.findByType(type) : entityRepository.findAll();
    }

    public Entity update(String id, Map<String, Object> propertyUpdates) {
        Entity entity = getById(id);
        EntityTypeDefinition typeDefinition = entityTypeService.getByName(entity.getType());
        Map<String, Object> merged = entity.getProperties();
        merged.putAll(propertyUpdates);
        schemaValidator.validateEntityProperties(typeDefinition, merged); // 422 on violation (FR-013)
        entity.setProperties(merged);
        entity.setIdentifyingValue(identifyingValueOf(typeDefinition, merged));
        return entityRepository.save(entity);
    }

    public void delete(String id, boolean cascade) {
        getById(id); // 404 if unknown
        long relationshipCount = relationshipRepository.countForEntity(id);
        if (relationshipCount > 0) {
            if (!cascade) {
                throw new ConflictException(
                        "Entity '" + id + "' has " + relationshipCount + " relationship(s); pass cascade=true to delete anyway"); // FR-005
            }
            relationshipRepository.deleteAllForEntity(id); // FR-005 cascade, SC-004 no orphans
        }
        entityRepository.deleteById(id);
    }

    private void checkDuplicate(EntityTypeDefinition typeDefinition, Map<String, Object> properties) {
        String identifyingValue = identifyingValueOf(typeDefinition, properties);
        if (identifyingValue == null) {
            return;
        }
        entityRepository
                .findByTypeAndIdentifyingValue(typeDefinition.getName(), identifyingValue)
                .ifPresent(existing -> {
                    throw new ConflictException("Duplicate entity: type '" + typeDefinition.getName() + "' with "
                            + typeDefinition.getIdentifyingProperty() + "=" + identifyingValue + " already exists");
                });
    }

    /** Canonical string form of the identifying property's value, or null if not yet set. */
    private String identifyingValueOf(EntityTypeDefinition typeDefinition, Map<String, Object> properties) {
        Object value = properties.get(typeDefinition.getIdentifyingProperty());
        return value != null ? String.valueOf(value) : null;
    }

    private String errorMessage(RuntimeException exception) {
        return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
    }
}
