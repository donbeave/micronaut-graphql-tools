package example

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.OptionalInt

@Singleton
@GraphQLRootResolver
@Requires(env = ["prod"])
class Query {

    fun count(): OptionalInt = OptionalInt.of(3)

}
