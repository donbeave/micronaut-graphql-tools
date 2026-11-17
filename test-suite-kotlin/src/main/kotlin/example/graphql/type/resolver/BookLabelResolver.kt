package example.graphql.type.resolver

import example.ElapsedTime
import example.data.Book
import jakarta.inject.Singleton

@Singleton
open class BookLabelResolver : BookLabelResolverContract {

    @ElapsedTime
    override fun label(book: Book): String = "${book.title} (${book.year})"
}
