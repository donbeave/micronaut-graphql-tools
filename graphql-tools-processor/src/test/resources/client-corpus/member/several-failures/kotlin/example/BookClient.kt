package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("{ nope }")
    fun first(): Any?

    @GraphQLOperation("{ version }")
    fun fine(): Any?

    @GraphQLOperation("Missing")
    fun second(): Any?

}
