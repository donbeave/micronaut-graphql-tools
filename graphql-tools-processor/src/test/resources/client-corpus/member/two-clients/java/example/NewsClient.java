package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "news")
public interface NewsClient {

    @GraphQLOperation("{ headline }")
    Object headline();

    @GraphQLOperation("{ version }")
    Object wrong();

}
