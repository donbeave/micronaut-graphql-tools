package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("query Book($id: ID!) { book(id: $id) { title } }")
    String book(String id);

    @GraphQLOperation("{ version }")
    String versionOnly();

    @GraphQLOperation
    String plain();

}
