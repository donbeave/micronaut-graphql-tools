package example;

import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class OtherBookQuery implements BookQueryContract {

    @Override
    public Book book(String id) {
        return null;
    }

    @Override
    public List<Book> books(Integer limit) {
        return null;
    }

}
