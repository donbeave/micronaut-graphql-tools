package example

import io.micronaut.context.annotation.Replaces
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.OptionalInt

@GraphQLRootResolver
@Replaces(LibraryQuery::class)
class ApplicationQuery {

    fun count(): OptionalInt = OptionalInt.of(3)

}
