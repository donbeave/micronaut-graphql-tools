package example

import io.micronaut.graphql.tools.resolver.TypeResolver

interface BookSummaryContract : TypeResolver<Book> {

    suspend fun describe(book: Book): String

}
