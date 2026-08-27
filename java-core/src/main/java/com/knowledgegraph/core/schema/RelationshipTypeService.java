package com.knowledgegraph.core.schema;

import com.knowledgegraph.core.exception.ConflictException;
import com.knowledgegraph.core.exception.NotFoundException;
import com.knowledgegraph.core.exception.SchemaValidationException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RelationshipTypeService {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private final RelationshipTypeDefinitionRepository repository;
    private final EntityTypeDefinitionRepository entityTypeRepository;

    public RelationshipTypeService(
            RelationshipTypeDefinitionRepository repository, EntityTypeDefinitionRepository entityTypeRepository) {
        this.repository = repository;
        this.entityTypeRepository = entityTypeRepository;
    }

    public RelationshipTypeDefinition create(
            String name,
            List<String> allowedSourceTypes,
            List<String> allowedTargetTypes,
            List<PropertyDefinition> properties) {
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new SchemaValidationException(
                    "Relationship type name '" + name + "' must match " + NAME_PATTERN.pattern());
        }
        if (repository.existsById(name)) {
            throw new ConflictException("Relationship type '" + name + "' already exists"); // FR-017
        }
        for (String entityType : allowedSourceTypes) {
            if (!entityTypeRepository.existsById(entityType)) {
                throw new SchemaValidationException("allowedSourceTypes references unknown entity type '" + entityType + "'");
            }
        }
        for (String entityType : allowedTargetTypes) {
            if (!entityTypeRepository.existsById(entityType)) {
                throw new SchemaValidationException("allowedTargetTypes references unknown entity type '" + entityType + "'");
            }
        }
        RelationshipTypeDefinition definition =
                new RelationshipTypeDefinition(name, allowedSourceTypes, allowedTargetTypes, properties);
        return repository.save(definition);
    }

    public List<RelationshipTypeDefinition> list() {
        return repository.findAll();
    }

    public RelationshipTypeDefinition getByName(String name) {
        return repository
                .findById(name)
                .orElseThrow(() -> new NotFoundException("Unknown relationship type '" + name + "'")); // FR-018
    }
}
