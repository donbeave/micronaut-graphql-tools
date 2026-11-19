package example;

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
@Requires(env = "prod")
public class HelloQuery {

    public String hello() {
        return "prod";
    }

}
