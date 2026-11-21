package example

import io.micronaut.graphql.tools.resolver.TypeResolver

interface BookSummaryContract extends TypeResolver<Book> {

    String summary(Book book)

}
