package example.graphql.type.resolver;

import example.ElapsedTime;
import example.data.Book;
import jakarta.inject.Singleton;

@Singleton
public class BookLabelResolver implements BookLabelResolverContract {

    @ElapsedTime
    @Override
    public String label(Book book) {
        return book.getTitle() + " (" + book.getYear() + ")";
    }

}
