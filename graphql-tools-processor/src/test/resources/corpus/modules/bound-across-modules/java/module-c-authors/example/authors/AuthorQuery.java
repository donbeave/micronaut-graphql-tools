package example.authors;

import jakarta.inject.Singleton;
import example.model.*;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;

import java.util.List;

@Singleton
@GraphQLRootResolver
public class AuthorQuery {

    public List<Author> authors(Genre genre) {
        return List.of(new Author("1", "Frank"));
    }

}
