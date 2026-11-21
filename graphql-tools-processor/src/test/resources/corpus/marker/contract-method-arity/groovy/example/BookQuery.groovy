package example

import jakarta.inject.Singleton

@Singleton
class BookQuery implements BookQueryContract {

    @Override
    Book book(String id) {
        return new Book()
    }

    Book book(String id, int limit) {
        return new Book()
    }

}
