package example

import jakarta.inject.Singleton
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
@Requires(env = ["prod"])
class HelloQuery {

    fun hello(): String = "prod"

}
