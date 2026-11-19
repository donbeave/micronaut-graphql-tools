package example.authors;

import example.model.*;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@GraphQLRootResolver
public class AuthorQuery {

    public List<Author> authors(Genre kind) {
        return List.of(new Author("1", "Frank"));
    }

}
