package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient
public interface BookClient {

    @GraphQLOperation("{ version }")
    String version();

}
