package example.data

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected

@CompileStatic
@Introspected
class BookSeller {
    final String bookId
    final String name
    final Float price

    BookSeller(String bookId, String name, Float price) {
        this.bookId = bookId
        this.name = name
        this.price = price
    }
}
