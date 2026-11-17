package example.host;

import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * An application that builds the {@link GraphQL} engine itself: the application supplies the
 * {@code TypeDefinitionRegistry} (here {@code example.graphql.GraphQLFactory}), the library binds it and offers the
 * {@link GraphQLSchema} bean, and the application builds {@code GraphQL} from that schema with its own configuration.
 * The library then creates no {@code GraphQL} bean of its own.
 */
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
