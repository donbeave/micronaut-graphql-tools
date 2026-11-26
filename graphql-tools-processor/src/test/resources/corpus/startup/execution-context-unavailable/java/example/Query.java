package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

@Singleton
@GraphQLRootResolver
public class Query {

    public String hello(String prefix, HttpRequest<?> request) {
        return prefix;
    }

}
