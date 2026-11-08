package example.graphql.type.resolver;

import example.ElapsedTime;
import example.data.Book;
import jakarta.inject.Singleton;

/**
 * Implements a resolver contract: a type resolver for {@code Book} through {@link BookLabelResolverContract}.
 */
@Singleton
public class BookLabelResolver implements BookLabelResolverContract {

    @ElapsedTime
    @Override
    public String label(Book book) {
        return book.getTitle() + " (" + book.getYear() + ")";
    }

}
