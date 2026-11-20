package example

import io.micronaut.graphql.tools.resolver.QueryResolver
import jakarta.inject.Singleton

@Singleton
open class BookQuery : QueryResolver {

    open fun book(id: String): Book = Book()

    open fun stray(x: String): String = x

}
