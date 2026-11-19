package example.authors;

import example.model.Author;
import example.model.Book;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import jakarta.inject.Singleton;

@Singleton
@GraphQLTypeResolver(Book.class)
public class BookAuthors {

    public Author author(Book book) {
        return new Author("1", "Frank");
    }

}
