package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@Answering
@GraphQLRootResolver
public interface Query {

    String hello();

    String goodbye();

}
