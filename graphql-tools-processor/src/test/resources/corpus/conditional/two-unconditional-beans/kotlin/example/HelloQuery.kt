package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class HelloQuery {

    fun hello(): String = "hello"

}
