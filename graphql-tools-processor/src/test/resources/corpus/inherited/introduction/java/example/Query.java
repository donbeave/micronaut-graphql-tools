package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Answering
@GraphQLRootResolver
public interface Query {

    String hello();

    String goodbye();

}
