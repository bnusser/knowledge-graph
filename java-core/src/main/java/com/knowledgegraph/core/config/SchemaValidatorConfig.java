package com.knowledgegraph.core.config;

import org.springframework.context.annotation.Configuration;

/**
 * Marks the schema-validator wiring point (T042). No explicit {@code @Bean} is defined here:
 * {@link com.knowledgegraph.core.schema.SchemaValidatorImpl} is itself {@code @Component}-annotated
 * and is the sole {@code SchemaValidator} bean Spring finds, superseding the Foundational-phase
 * {@link com.knowledgegraph.core.schema.NoOpSchemaValidator} placeholder (never itself a bean).
 */
@Configuration
public class SchemaValidatorConfig {
}
