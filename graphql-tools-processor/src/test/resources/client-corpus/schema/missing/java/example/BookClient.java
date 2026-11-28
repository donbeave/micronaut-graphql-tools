package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "library")
public interface BookClient {

    @GraphQLOperation("{ version }")
    Object first();

    @GraphQLOperation("{ version }")
    Object second();

}
