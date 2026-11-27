package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.graphql.tools.client.annotation.GraphQLVariable;
import io.micronaut.http.annotation.QueryValue;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("query Book($id: ID!) { book(id: $id) { title } }")
    Object book(String id, int limit);

    @GraphQLOperation("query Book($id: ID!) { book(id: $id) { title } }")
    Object tagged(String id, @GraphQLVariable("id") String copy);

    @GraphQLOperation("query Book($id: ID!) { book(id: $id) { title } }")
    Object queried(String id, @QueryValue String page);

}
