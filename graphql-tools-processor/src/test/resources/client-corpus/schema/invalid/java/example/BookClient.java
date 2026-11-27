package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "broken")
public interface BookClient {

    @GraphQLOperation("{ a }")
    Object first();

    @GraphQLOperation("{ a }")
    Object second();

}
