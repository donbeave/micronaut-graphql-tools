package example

import jakarta.inject.Singleton

@Singleton
class BookQuery implements BookQueryContract {

    @Override
    Book book(String id) {
        return new Book()
    }

    String describe() {
        return 'not a field'
    }

}
