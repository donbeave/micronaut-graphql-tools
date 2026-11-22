package example

import io.micronaut.graphql.tools.resolver.TypeResolver

interface BookSummaryContract : TypeResolver<Book> {

    fun summary(book: Book): String

}
