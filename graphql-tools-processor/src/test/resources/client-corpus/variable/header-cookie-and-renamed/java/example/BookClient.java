package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.graphql.tools.client.annotation.GraphQLVariable;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Header;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("query Book($id: ID!) { book(id: $id) { title } }")
    Object book(@GraphQLVariable("id") String bookId, @Header("X-Trace") String trace, @CookieValue("session") String session);

}
