package example

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.OptionalInt

@GraphQLRootResolver
@Requires(env = ["prod"])
class Query {

    fun count(): OptionalInt = OptionalInt.of(3)

}
