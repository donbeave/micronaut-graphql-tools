package example.first;

import jakarta.inject.Singleton;
import example.model.*;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class FirstQuery {

    public Book book(String id) {
        return new Book(id, "Dune");
    }

}
