package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Singleton
@GraphQLRootResolver
class Subscription {

    fun published(): Flow<Book> = flowOf(Book("1", "Dune"))

}
