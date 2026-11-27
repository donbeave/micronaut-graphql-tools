package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("{ nope }")
    fun first(): String

    @GraphQLOperation("{ version }")
    fun fine(): String

    @GraphQLOperation("Missing")
    fun second(): String

}
