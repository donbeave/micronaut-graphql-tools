package example.directives;

import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.graphql.tools.SchemaDirective;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

@Factory
public class DirectiveFixtures {

    public static final String CONFLICT = "directive-conflict";
    public static final String UNWIRED = "directive-unwired";
    public static final String SCHEMA_UNLISTED = "directive-schema-unlisted";

    @Bean
    @Singleton
    @Requires(env = CONFLICT)
    SchemaDirective upperForEveryElement() {
        return SchemaDirective.everyElement(List.of("upper"), new SchemaDirectiveWiring() { });
    }

    @Bean
    @Singleton
    @Requires(env = UNWIRED)
    @Replaces(TypeDefinitionRegistry.class)
    TypeDefinitionRegistry unwiredDirectiveRegistry(ResourceResolver resourceResolver) {
        return registryWith(resourceResolver, "directive @legacy on OBJECT\nextend type Book @legacy");
    }

    @Bean
    @Singleton
    @Requires(env = SCHEMA_UNLISTED)
    @Replaces(TypeDefinitionRegistry.class)
    TypeDefinitionRegistry schemaDirectiveRegistry(ResourceResolver resourceResolver) {
        return registryWith(resourceResolver, "directive @other on SCHEMA\nextend schema @other");
    }

    private static TypeDefinitionRegistry registryWith(ResourceResolver resourceResolver, String extension) {
        SchemaParser parser = new SchemaParser();
        return new TypeDefinitionRegistry()
                .merge(parser.parse(new BufferedReader(new InputStreamReader(
                        resourceResolver.getResourceAsStream("classpath:schema.graphqls").orElseThrow()))))
                .merge(parser.parse(extension));
    }

}
