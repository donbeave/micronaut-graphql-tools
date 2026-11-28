package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "empty")
public interface BookClient {

    @GraphQLOperation("{ version }")
    Object version();

}
