package example;

import jakarta.inject.Singleton;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@Singleton
@GraphQLRootResolver
public class Query {

    public String page(Integer first, Integer offset, Integer limit) {
        return "first=" + first + " offset=" + offset + " limit=" + limit;
    }

}
