package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    fun book(id: String): Book? = null

    fun search(filter: BookFilter?): List<Book> = emptyList()

}
