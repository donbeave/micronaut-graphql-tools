package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Query {

    public String page(Integer count, Integer first) {
        return "first=" + first + " count=" + count;
    }

}
