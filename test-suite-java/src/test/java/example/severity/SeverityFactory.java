package example.severity;

import example.graphql.GraphQLFactory;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.ResourceResolver;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Factory
@Requires(env = SeverityFactory.ENVIRONMENT)
public class SeverityFactory {

    public static final String ENVIRONMENT = "check-severity";

    // The example schema and a type that no field returns, which the unreachable-type check finds
    @Bean
    @Singleton
    @Replaces(value = TypeDefinitionRegistry.class, factory = GraphQLFactory.class)
    public TypeDefinitionRegistry typeDefinitionRegistry(ResourceResolver resourceResolver) throws IOException {
        SchemaParser parser = new SchemaParser();
        try (InputStream example = resourceResolver.getResourceAsStream("classpath:schema.graphqls").orElseThrow()) {
            return new TypeDefinitionRegistry()
                    .merge(parser.parse(new String(example.readAllBytes(), StandardCharsets.UTF_8)))
                    .merge(parser.parse("type Orphan { id: ID }\n"));
        }
    }

}
