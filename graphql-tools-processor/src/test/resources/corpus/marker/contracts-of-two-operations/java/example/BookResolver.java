package example;

import jakarta.inject.Singleton;

@Singleton
public class BookResolver implements BookQueryContract, BookMutationContract {

    @Override
    public Book book(String id) {
        return new Book();
    }

    @Override
    public Book book(String id, String title) {
        return new Book();
    }

}
