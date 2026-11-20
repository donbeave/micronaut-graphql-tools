package example

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Replaces
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.OptionalInt

@Singleton
@GraphQLRootResolver
@Replaces(LibraryQuery::class)
class ApplicationQuery {

    fun count(): OptionalInt = OptionalInt.of(3)

}
