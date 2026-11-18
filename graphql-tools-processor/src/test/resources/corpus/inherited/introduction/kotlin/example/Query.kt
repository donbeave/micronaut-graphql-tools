package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Answering
@GraphQLRootResolver
interface Query {

    fun hello(): String

    fun goodbye(): String

}
