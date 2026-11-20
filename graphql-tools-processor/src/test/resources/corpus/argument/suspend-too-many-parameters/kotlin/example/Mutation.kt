package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Mutation {

    suspend fun rename(id: String, title: String): Book = Book(id, title)

}
