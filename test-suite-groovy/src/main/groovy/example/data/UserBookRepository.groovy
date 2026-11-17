package example.data

import groovy.transform.CompileStatic
import jakarta.inject.Singleton

@CompileStatic
@Singleton
class UserBookRepository {

    static class DuplicateException extends RuntimeException {
    }

    private static final Map<Long, List<Book>> STATE = [:]

    List<Book> findAllByUserId(User user) {
        STATE.getOrDefault(user.id, Collections.<Book> emptyList())
    }

    void addBook(User user, Book book) {
        List<Book> books = STATE.computeIfAbsent(user.id) { Long k -> new ArrayList<Book>() }
        if (books.contains(book)) {
            throw new DuplicateException()
        }
        books.add(book)
    }
}
