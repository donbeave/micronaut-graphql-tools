package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("query Q($x: Int) { notInTheSchema }")
    String missing();

    @GraphQLOperation("{ alsoNot another }")
    String two();

}
