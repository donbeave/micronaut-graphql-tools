package example.data

import jakarta.inject.Singleton

@Singleton
class BookSellerRepository {

    fun findAllByBookIds(bookIds: Collection<String>): List<BookSeller> = STATE.filter { it.bookId in bookIds }

    companion object {
        private val STATE = listOf(
            BookSeller("ab", "Amaz", 20.00f),
            BookSeller("ab", "Yah", 21.00f),
            BookSeller("ax", "Amaz", 19.00f),
            BookSeller("gd", "Zap", 19.50f),
            BookSeller("we", "Amaz", 36.00f),
            BookSeller("we", "Yah", 35.00f),
            BookSeller("we", "Zap", 34.00f),
        )
    }
}
