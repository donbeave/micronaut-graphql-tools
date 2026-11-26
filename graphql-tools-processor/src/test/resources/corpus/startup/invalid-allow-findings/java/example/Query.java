package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLRootResolver
public class Query {

    public String hello() {
        return "hello";
    }

}
