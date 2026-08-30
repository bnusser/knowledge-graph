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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class EntityService {

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
        Map<String, EntityTypeDefinition> typeDefinitions = new HashMap<>();
        List<EntityCandidate> candidates = new ArrayList<>();

        for (int index = 0; index < requests.size(); index++) {
            EntityCreateRequest request = requests.get(index);
            try {
                EntityTypeDefinition typeDefinition = typeDefinitions.computeIfAbsent(
                        request.type(), entityTypeService::getByName); // one schema lookup per type
                schemaValidator.validateEntityProperties(typeDefinition, request.properties()); // 422 (FR-013)
                String identifyingValue = identifyingValueOf(typeDefinition, request.properties());
                candidates.add(new EntityCandidate(index, request, identifyingValue));
            } catch (RuntimeException exception) {
                results.set(index, BulkItemResult.rejected(index, errorMessage(exception)));
            }
        }

        Map<EntityKey, Entity> knownEntities = new HashMap<>();
        Map<String, List<String>> valuesByType = candidates.stream()
                .filter(candidate -> candidate.identifyingValue() != null)
                .collect(Collectors.groupingBy(
                        candidate -> candidate.request().type(),
                        LinkedHashMap::new,
                        Collectors.mapping(EntityCandidate::identifyingValue, Collectors.toList())));
        valuesByType.forEach((type, values) -> entityRepository
                .findAllByTypeAndIdentifyingValueIn(type, values.stream().distinct().toList())
                .forEach(entity -> knownEntities.put(
                        new EntityKey(entity.getType(), entity.getIdentifyingValue()), entity)));

        List<PreparedEntity> toPersist = new ArrayList<>();
        for (EntityCandidate candidate : candidates) {
            EntityKey key = candidate.identifyingValue() == null
                    ? null
                    : new EntityKey(candidate.request().type(), candidate.identifyingValue());
            Entity existing = key == null ? null : knownEntities.get(key);
            if (existing != null) {
                results.set(candidate.index(), BulkItemResult.alreadyPresent(candidate.index(), existing.getId()));
                continue;
            }
            Entity entity = new Entity(
                    UUID.randomUUID().toString(),
                    candidate.request().type(),
                    candidate.request().properties());
            entity.setIdentifyingValue(candidate.identifyingValue());
            PreparedEntity prepared = new PreparedEntity(candidate.index(), entity);
            toPersist.add(prepared);
            if (key != null) {
                // Later duplicates in this same request resolve to the ID assigned here.
                knownEntities.put(key, entity);
            }
        }

        if (!toPersist.isEmpty()) {
            try {
                entityRepository.createBatch(toPersist.stream().map(this::toRow).toList());
                for (PreparedEntity prepared : toPersist) {
                    results.set(
                            prepared.index(),
                            BulkItemResult.created(prepared.index(), prepared.entity().getId()));
                }
            } catch (RuntimeException exception) {
                persistEntitiesIndividually(toPersist, results);
            }
        }
        return new BulkResult(results);
    }

    private void persistEntitiesIndividually(
            List<PreparedEntity> entities, List<BulkItemResult> results) {
        for (PreparedEntity prepared : entities) {
            try {
                entityRepository.createBatch(List.of(toRow(prepared)));
                results.set(
                        prepared.index(),
                        BulkItemResult.created(prepared.index(), prepared.entity().getId()));
            } catch (RuntimeException exception) {
                String identifyingValue = prepared.entity().getIdentifyingValue();
                if (identifyingValue != null) {
                    var existing = entityRepository.findByTypeAndIdentifyingValue(
                            prepared.entity().getType(), identifyingValue);
                    if (existing.isPresent()) {
                        results.set(
                                prepared.index(),
                                BulkItemResult.alreadyPresent(prepared.index(), existing.get().getId()));
                        continue;
                    }
                }
                results.set(
                        prepared.index(),
                        BulkItemResult.rejected(prepared.index(), errorMessage(exception)));
            }
        }
    }

    private Map<String, Object> toRow(PreparedEntity prepared) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", prepared.entity().getId());
        row.put("type", prepared.entity().getType());
        row.put("propertiesJson", prepared.entity().getPropertiesJson());
        row.put("identifyingValue", prepared.entity().getIdentifyingValue());
        return row;
    }

    private record EntityCandidate(int index, EntityCreateRequest request, String identifyingValue) {}

    private record EntityKey(String type, String identifyingValue) {}

    private record PreparedEntity(int index, Entity entity) {}

    public Entity getById(String id) {
        return entityRepository.findById(id).orElseThrow(() -> new NotFoundException("Unknown entity '" + id + "'"));
    }

    public Map<String, Entity> getByIds(Set<String> ids) {
        Map<String, Entity> entities = new HashMap<>();
        entityRepository.findAllById(ids).forEach(entity -> entities.put(entity.getId(), entity));
        return entities;
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
