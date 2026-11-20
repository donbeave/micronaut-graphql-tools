package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.concurrent.CompletionStage

@Singleton
@GraphQLRootResolver
class BookQuery {

    fun book(id: String): Book = Book(id)

    fun books(limit: Int?): CompletionStage<*>? = null

}
