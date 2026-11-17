package example.data

import groovy.transform.CompileStatic
import jakarta.inject.Singleton

@CompileStatic
@Singleton
class BookSellerRepository {

    private static final List<BookSeller> STATE = [
            new BookSeller('ab', 'Amaz', 20.00f),
            new BookSeller('ab', 'Yah', 21.00f),
            new BookSeller('ax', 'Amaz', 19.00f),
            new BookSeller('gd', 'Zap', 19.50f),
            new BookSeller('we', 'Amaz', 36.00f),
            new BookSeller('we', 'Yah', 35.00f),
            new BookSeller('we', 'Zap', 34.00f),
    ]

    List<BookSeller> findAllByBookIds(Collection<String> bookIds) {
        STATE.findAll { bookIds.contains(it.bookId) }
    }
}
