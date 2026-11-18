package example.qualified;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.StaticDataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
@Requires(env = QualifiedBeansFactory.ENVIRONMENT)
public class QualifiedBeansFactory {

    public static final String ENVIRONMENT = "qualified-graphql";

    @Bean
    @Singleton
    @Named("admin")
    GraphQLSchema adminSchema() {
        return new SchemaGenerator().makeExecutableSchema(
                new SchemaParser().parse("schema { query: AdminQuery } type AdminQuery { secret: String }"),
                RuntimeWiring.newRuntimeWiring()
                        .type("AdminQuery", type -> type.dataFetcher("secret", new StaticDataFetcher("admin")))
                        .build());
    }

    @Bean
    @Singleton
    @Named("admin")
    GraphQL adminGraphQL(@Named("admin") GraphQLSchema schema) {
        return GraphQL.newGraphQL(schema).build();
    }

}
