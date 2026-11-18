package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
open class Query {

    open fun label(): String = "label"

}
