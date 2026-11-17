package example.host;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Factory
@Requires(env = HostGraphQLFactory.ENVIRONMENT)
public class HostGraphQLFactory {

    public static final String ENVIRONMENT = "host-graphql";

    @Bean
    @Singleton
    GraphQL graphQL(GraphQLSchema schema, OperationCounter operationCounter) {
        return GraphQL.newGraphQL(schema)
                .instrumentation(operationCounter)
                .build();
    }

}
