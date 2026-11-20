package example;

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.concurrent.CompletionStage;

@GraphQLRootResolver
public class BookQuery {

    public Book book(String id) {
        return new Book(id);
    }

    @SuppressWarnings("rawtypes")
    public CompletionStage books(Integer limit) {
        return null;
    }

}
