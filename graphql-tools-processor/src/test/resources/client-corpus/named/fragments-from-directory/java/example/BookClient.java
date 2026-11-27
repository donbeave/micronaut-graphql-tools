package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("{ books { ...Titled ...Authored } } fragment Titled on Book { id }")
    String inline();

    @GraphQLOperation("Named")
    String named();

}
