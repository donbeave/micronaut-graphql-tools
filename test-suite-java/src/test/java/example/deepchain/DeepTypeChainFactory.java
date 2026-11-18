package example.deepchain;

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
@Requires(env = DeepTypeChainFactory.ENVIRONMENT)
public class DeepTypeChainFactory {

    public static final String ENVIRONMENT = "deep-type-chain";

    public static final int TYPES = 1000;

    // The example schema and a chain of types that extends its Query. One Java class backs every type of the chain: it is
    // the length of the chain of schema types that matters.
    @Bean
    @Singleton
    @Replaces(value = TypeDefinitionRegistry.class, factory = GraphQLFactory.class)
    public TypeDefinitionRegistry typeDefinitionRegistry(ResourceResolver resourceResolver) throws IOException {
        StringBuilder sdl = new StringBuilder("extend type Query { chainRoot: Node0 chainEcho(input: In0!): String! }\n");
        for (int i = 0; i < TYPES; i++) {
            int next = (i + 1) % TYPES;
            sdl.append("type Node").append(i).append(" { name: String! next: Node").append(next).append(" }\n");
            sdl.append("input In").append(i).append(" { name: String! next: In").append(next).append(" }\n");
        }
        SchemaParser parser = new SchemaParser();
        try (InputStream example = resourceResolver.getResourceAsStream("classpath:schema.graphqls").orElseThrow()) {
            return new TypeDefinitionRegistry()
                    .merge(parser.parse(new String(example.readAllBytes(), StandardCharsets.UTF_8)))
                    .merge(parser.parse(sdl.toString()));
        }
    }

}
