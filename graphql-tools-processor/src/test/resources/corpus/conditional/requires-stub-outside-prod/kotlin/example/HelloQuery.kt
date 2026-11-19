package example

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
@Requires(env = ["prod"])
class HelloQuery {

    fun hello(): String = "prod"

}
