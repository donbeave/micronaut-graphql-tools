package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
@Requires(notEnv = "prod")
public class HelloQueryStub {

    public String hello() {
        return "stub";
    }

}
