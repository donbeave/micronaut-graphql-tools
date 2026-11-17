package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public String hello() {
        return "world";
    }

    public Book book() {
        return new Book();
    }

}
