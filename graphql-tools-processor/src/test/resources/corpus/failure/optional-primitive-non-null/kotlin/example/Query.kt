package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.OptionalInt

@GraphQLRootResolver
class Query {

    fun count(): OptionalInt = OptionalInt.of(3)

}
