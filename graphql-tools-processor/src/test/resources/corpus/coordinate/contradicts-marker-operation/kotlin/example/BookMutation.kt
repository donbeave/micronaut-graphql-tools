package example

import io.micronaut.graphql.tools.annotation.GraphQLSchemaCoordinate
import jakarta.inject.Singleton

@Singleton
open class BookMutation : BookMutationContract {

    @GraphQLSchemaCoordinate("Query.book")
    override fun rename(id: String): Book = Book()

}
