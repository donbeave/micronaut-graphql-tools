package example.graphql.query

import example.ElapsedTime
import example.data.Book
import example.data.BookRepository
import io.micronaut.context.event.StartupEvent
import io.micronaut.graphql.tools.annotation.GraphQLIgnore
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.runtime.event.annotation.EventListener

@GraphQLRootResolver
open class AllBooksQuery(private val bookRepository: BookRepository) {

    @ElapsedTime
    open fun allBooks(): List<Book> = bookRepository.findAll()

    // binds no field: strict mode skips an event listener (D-30)
    @EventListener
    open fun onStartup(event: StartupEvent) {
    }

    // binds no field: marked, so strict mode does not report it, and it is listed in the binding manifest
    @GraphQLIgnore
    open fun helper(value: String): String = value
}
