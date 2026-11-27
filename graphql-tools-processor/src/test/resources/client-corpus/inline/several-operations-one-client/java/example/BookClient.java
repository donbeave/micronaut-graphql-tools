package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("query Book($id: ID!) { book(id: $id) { title } }")
    Object book(String id);

    @GraphQLOperation("{ version }")
    Object versionOnly();

    @GraphQLOperation
    Object plain();

}
