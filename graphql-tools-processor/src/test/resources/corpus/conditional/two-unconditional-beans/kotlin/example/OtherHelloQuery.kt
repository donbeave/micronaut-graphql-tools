package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class OtherHelloQuery {

    fun hello(): String = "other"

}
