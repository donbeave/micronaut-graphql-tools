package example;

import jakarta.inject.Singleton;

@Singleton
public class BookResolver implements BookResolverContract {

    @Override
    public String summary(Book book) {
        return "Summary of " + book.getTitle();
    }

}
