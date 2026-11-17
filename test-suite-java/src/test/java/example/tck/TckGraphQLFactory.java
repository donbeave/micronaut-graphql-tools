package example.tck;

import example.graphql.GraphQLFactory;
import example.graphql.type.SecurityError;
import example.graphql.type.ValidationError;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.graphql.tools.SchemaMappingDictionaryCustomizer;
import io.micronaut.graphql.tools.tck.Scenarios;
import io.micronaut.graphql.tools.tck.Tck;
import jakarta.inject.Singleton;
import org.dataloader.DataLoaderRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * While a TCK scenario runs, the application serves the example schema and the extensions of that scenario's schema.
 */
@Factory
@Requires(property = Tck.SCENARIO_PROPERTY)
public class TckGraphQLFactory {

    @Bean
    @Singleton
    @Replaces(value = TypeDefinitionRegistry.class, factory = GraphQLFactory.class)
    public TypeDefinitionRegistry typeDefinitionRegistry(@Property(name = Tck.SCENARIO_PROPERTY) String scenario,
                                                         ResourceResolver resourceResolver) throws IOException {
        SchemaParser parser = new SchemaParser();
        try (InputStream example = resourceResolver.getResourceAsStream("classpath:schema.graphqls").orElseThrow()) {
            return new TypeDefinitionRegistry()
                    .merge(parser.parse(new String(example.readAllBytes(), StandardCharsets.UTF_8)))
                    .merge(parser.parse(Scenarios.schema(scenario)));
        }
    }

    // The example registers the classes of its union by name; a scenario with classes of that kind adds its own mappings.
    @Bean
    @Singleton
    @Replaces(value = SchemaMappingDictionaryCustomizer.class, factory = GraphQLFactory.class)
    public SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer(List<TckTypeMappings> scenarioMappings) {
        return dictionary -> {
            dictionary
                    .registerType("SecurityError", SecurityError.class)
                    .registerType("ValidationError", ValidationError.class);
            scenarioMappings.forEach(mappings -> mappings.register(dictionary));
        };
    }

    // The example's registry is request scoped, and a request-scoped proxy has no request on the thread that completes a
    // CompletionStage. Every scenario but the one that tests a registry for each request (tck.registry=per-request) serves a
    // plain registry.
    @Bean
    @Singleton
    @Primary
    @Requires(missingProperty = "tck.registry")
    public DataLoaderRegistry dataLoaderRegistry() {
        return new DataLoaderRegistry();
    }

}
