package com.knowledgegraph.core.schema;

/**
 * A single named, typed property rule within an {@link EntityTypeDefinition} or
 * {@link RelationshipTypeDefinition}. Not a Neo4j node itself: lists of these are stored as
 * JSON on the owning schema definition (see EntityTypeDefinition/RelationshipTypeDefinition),
 * since Neo4j properties can't natively hold a list of complex objects.
 */
public record PropertyDefinition(String name, PropertyDataType dataType, boolean required) {
}
