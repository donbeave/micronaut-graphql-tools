package example.eager;

import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.ResourceResolver;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * The application's schema plus a field that no resolver binds, in the {@value #ENVIRONMENT} environment only.
 */
@Factory
@Requires(env = BrokenBindingFactory.ENVIRONMENT)
public class BrokenBindingFactory {

    public static final String ENVIRONMENT = "broken-binding";

    @Bean
    @Singleton
    @Replaces(TypeDefinitionRegistry.class)
    TypeDefinitionRegistry brokenTypeDefinitionRegistry(ResourceResolver resourceResolver) {
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry registry = new TypeDefinitionRegistry();
        registry.merge(parser.parse(new BufferedReader(new InputStreamReader(
                resourceResolver.getResourceAsStream("classpath:schema.graphqls").orElseThrow()))));
        registry.merge(parser.parse("extend type Query { unbound: String }"));
        return registry;
    }

}
