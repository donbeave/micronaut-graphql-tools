package example.graphql.query

import example.ElapsedTime
import example.data.Book
import example.data.BookRepository
import io.micronaut.context.event.StartupEvent
import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.runtime.event.annotation.EventListener

// tag::resolver[]
@GraphQLRootResolver
open class AllBooksQuery(private val bookRepository: BookRepository) {

    @ElapsedTime
    open fun allBooks(): List<Book> = bookRepository.findAll()

    @EventListener
    open fun onStartup(event: StartupEvent) {
    }

    @GraphQLIgnore
    open fun helper(value: String): String = value
}
// end::resolver[]
