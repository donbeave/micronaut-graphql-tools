package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

@GraphQLRootResolver
public class Query {

    public Book book(String id) {
        return new Book(id, "Dune");
    }

}
