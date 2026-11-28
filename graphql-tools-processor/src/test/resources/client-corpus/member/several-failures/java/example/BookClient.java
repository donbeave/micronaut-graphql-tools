package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("{ nope }")
    Object first();

    @GraphQLOperation("{ version }")
    Object fine();

    @GraphQLOperation("Missing")
    Object second();

}
