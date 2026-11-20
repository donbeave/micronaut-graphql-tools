package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import java.util.OptionalInt

@Singleton
@GraphQLRootResolver
class Query {

    fun count(): OptionalInt = OptionalInt.of(3)

}
