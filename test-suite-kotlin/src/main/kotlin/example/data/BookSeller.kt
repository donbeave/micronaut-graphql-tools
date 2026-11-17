package example.data

import io.micronaut.core.annotation.Introspected

@Introspected
class BookSeller(val bookId: String, val name: String, val price: Float)
