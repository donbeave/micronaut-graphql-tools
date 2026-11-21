package example;

import jakarta.inject.Singleton;

@Singleton
public class BookQuery implements BookQueryContract {

    @Override
    public Book book(String id) {
        return new Book();
    }

    public Book book(String id, int limit) {
        return new Book();
    }

}
