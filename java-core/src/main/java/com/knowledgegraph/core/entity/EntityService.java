package com.knowledgegraph.core.entity;

import com.knowledgegraph.core.exception.ConflictException;
import com.knowledgegraph.core.exception.NotFoundException;
import com.knowledgegraph.core.relationship.RelationshipRepository;
import com.knowledgegraph.core.schema.EntityTypeDefinition;
import com.knowledgegraph.core.schema.EntityTypeService;
import com.knowledgegraph.core.schema.SchemaValidator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
}
