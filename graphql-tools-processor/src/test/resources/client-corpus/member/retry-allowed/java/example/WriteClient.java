package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.retry.annotation.Retryable;

@GraphQLClient(schema = "books")
public interface WriteClient {

    @Retryable
    @GraphQLOperation("mutation Save($title: String!) { save(title: $title) { id } }")
    Object save(String title);

}
