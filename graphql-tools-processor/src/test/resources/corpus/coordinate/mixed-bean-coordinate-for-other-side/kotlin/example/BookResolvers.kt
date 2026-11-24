package example

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import jakarta.inject.Singleton

@Singleton
open class BookResolvers : BookQueryContract, BookSummaryContract {

    @GraphQLSchemaCoordinate("Book.title")
    override fun lookup(id: String): Book = Book()

    @GraphQLSchemaCoordinate("Book.summary")
    override fun describe(book: Book): String = "s"

}
