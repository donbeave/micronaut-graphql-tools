package example;

import jakarta.inject.Singleton;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
@Requires(notEnv = "prod")
public class HelloQueryStub {

    public String hello() {
        return "stub";
    }

}
