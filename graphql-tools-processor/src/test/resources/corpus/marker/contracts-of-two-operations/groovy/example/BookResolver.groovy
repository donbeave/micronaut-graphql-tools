package example

import jakarta.inject.Singleton

@Singleton
class BookResolver implements BookQueryContract, BookMutationContract {

    @Override
    Book book(String id) {
        return new Book()
    }

    @Override
    Book book(String id, String title) {
        return new Book()
    }

}
