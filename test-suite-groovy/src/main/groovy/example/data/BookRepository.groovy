package example.data

import groovy.transform.CompileStatic
import jakarta.inject.Singleton

@CompileStatic
@Singleton
class BookRepository {

    private static final List<Book> STATE = [
            new Book('ab', 'The Fractured Sage', 1982),
            new Book('ax', 'Considered for Duty', 2006),
            new Book('gd', 'Sword of Six', 1975),
            new Book('we', 'The Dark Boy', 2035),
            new Book('df', 'Strike of Arrakis', 2000),
    ]

    List<Book> findAll() {
        STATE
    }

    Book findById(String id) {
        STATE.find { it.id == id }
    }

    void save(Book book) {
        STATE.add(book)
    }
}
