package example;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;

@GraphQLClient(schema = "server")
public interface BookClient {

    @GraphQLOperation("{ version }")
    String version();

    @GraphQLOperation("FromServerDirectory")
    String named();

}
