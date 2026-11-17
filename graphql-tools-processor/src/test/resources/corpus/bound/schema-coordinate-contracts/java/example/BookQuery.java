package example;

import jakarta.inject.Singleton;

@Singleton
public class BookQuery implements BookQueryContract {

    @Override
    public Book book(String id) {
        return new Book(id, "Dune");
    }

}
