package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.retry.annotation.Retryable;

@Retryable
@GraphQLClient(schema = "books")
public interface BookClient {

    @GraphQLOperation("{ version }")
    Object version();

}
