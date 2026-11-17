package example.data

import jakarta.inject.Singleton

@Singleton
class UserBookRepository {

    class DuplicateException : RuntimeException()

    fun findAllByUserId(user: User): List<Book> = STATE[user.id] ?: emptyList()

    fun addBook(user: User, book: Book) {
        val books = STATE.getOrPut(user.id) { mutableListOf() }
        if (book in books) {
            throw DuplicateException()
        }
        books.add(book)
    }

    companion object {
        private val STATE = mutableMapOf<Long?, MutableList<Book>>()
    }
}
