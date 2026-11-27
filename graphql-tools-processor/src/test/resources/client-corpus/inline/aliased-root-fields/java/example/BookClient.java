package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("{ v: version }")
    Object one();

    @GraphQLOperation("{ a: version b: version }")
    Object two();

}
