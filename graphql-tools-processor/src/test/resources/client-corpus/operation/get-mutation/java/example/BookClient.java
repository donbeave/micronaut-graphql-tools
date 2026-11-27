package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation(value = "mutation Save($title: String!) { save(title: $title) { id } }", method = GraphQLOperation.Method.GET)
    Object save(String title);

    @GraphQLOperation(value = "subscription Added { bookAdded { id } }", method = GraphQLOperation.Method.GET)
    Object added();

    @GraphQLOperation(value = "{ version }", method = GraphQLOperation.Method.GET)
    Object version();

}
