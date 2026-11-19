package example.first;

import example.model.*;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class FirstQuery {

    public Book book(String id) {
        return new Book(id, "Dune");
    }

}
