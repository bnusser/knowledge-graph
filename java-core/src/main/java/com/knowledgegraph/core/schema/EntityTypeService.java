package com.knowledgegraph.core.schema;

import com.knowledgegraph.core.exception.ConflictException;
import com.knowledgegraph.core.exception.NotFoundException;
import com.knowledgegraph.core.exception.SchemaValidationException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
public class EntityTypeService {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private final EntityTypeDefinitionRepository repository;
    private final SchemaValidator schemaValidator;
    private final Neo4jClient neo4jClient;

    public EntityTypeService(
            EntityTypeDefinitionRepository repository, SchemaValidator schemaValidator, Neo4jClient neo4jClient) {
        this.repository = repository;
        this.schemaValidator = schemaValidator;
        this.neo4jClient = neo4jClient;
    }

    public EntityTypeDefinition create(String name, List<PropertyDefinition> properties, String identifyingProperty) {
        requireSafeName(name, "Entity type name");
        for (PropertyDefinition property : properties) {
            requireSafeName(property.name(), "Property name");
        }
        requireSafeName(identifyingProperty, "identifyingProperty");
        // FR-017: reject redefinition of an existing schema element, leaving it unchanged.
        if (repository.existsById(name)) {
            throw new ConflictException("Entity type '" + name + "' already exists");
        }
        EntityTypeDefinition definition = new EntityTypeDefinition(name, properties, identifyingProperty);
        schemaValidator.validateEntityTypeDefinition(definition);
        EntityTypeDefinition saved = repository.save(definition);
        createUniquenessConstraint(name, identifyingProperty); // FR-014, DB-level guarantee
        return saved;
    }

    public List<EntityTypeDefinition> list() {
        return repository.findAll();
    }

    public EntityTypeDefinition getByName(String name) {
        return repository
                .findById(name)
                .orElseThrow(() -> new NotFoundException("Unknown entity type '" + name + "'")); // FR-018
    }

    private void requireSafeName(String value, String fieldLabel) {
        if (value == null || !NAME_PATTERN.matcher(value).matches()) {
            throw new SchemaValidationException(fieldLabel + " '" + value + "' must match " + NAME_PATTERN.pattern());
        }
    }

    /**
     * Composite uniqueness constraint on (type, identifyingValue) — the same flat,
     * natively-indexable field {@link com.knowledgegraph.core.entity.EntityRepository
     * #findByTypeAndIdentifyingValue} queries — scoping duplicate detection per entity type
     * (FR-014). Without this index-backed match, every duplicate check degrades to a full
     * label scan as the graph grows.
     */
    private void createUniquenessConstraint(String entityTypeName, String identifyingProperty) {
        String constraintName = "uniq_" + entityTypeName.toLowerCase() + "_" + identifyingProperty.toLowerCase();
        String ddl = "CREATE CONSTRAINT " + constraintName + " IF NOT EXISTS FOR (e:Entity) "
                + "REQUIRE (e.type, e.identifyingValue) IS UNIQUE";
        neo4jClient.query(ddl).run();
    }
}

