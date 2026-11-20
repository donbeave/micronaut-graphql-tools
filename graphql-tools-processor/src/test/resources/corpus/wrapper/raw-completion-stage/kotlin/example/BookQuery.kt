package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.concurrent.CompletionStage

@GraphQLRootResolver
class BookQuery {

    fun book(id: String): Book = Book(id)

    fun books(limit: Int?): CompletionStage<*>? = null

}
