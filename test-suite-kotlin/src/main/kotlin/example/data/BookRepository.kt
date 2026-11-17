package example.data

import jakarta.inject.Singleton

@Singleton
class BookRepository {

    fun findAll(): List<Book> = STATE

    fun findById(id: String): Book? = STATE.firstOrNull { it.id == id }

    fun save(book: Book) {
        STATE.add(book)
    }

    companion object {
        private val STATE = mutableListOf(
            Book("ab", "The Fractured Sage", 1982),
            Book("ax", "Considered for Duty", 2006),
            Book("gd", "Sword of Six", 1975),
            Book("we", "The Dark Boy", 2035),
            Book("df", "Strike of Arrakis", 2000),
        )
    }
}
