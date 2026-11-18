package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
abstract class BaseQuery {

    open fun hello(): String = "base"

}
