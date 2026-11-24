package example

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import jakarta.inject.Singleton

@Singleton
open class BookResolvers : BookQueryContract, BookSummaryContract {

    @GraphQLSchemaCoordinate("Query.book")
    override suspend fun lookup(id: String): Book = Book()

    @GraphQLSchemaCoordinate("Book.summary")
    override suspend fun describe(book: Book): String = "s"

}
