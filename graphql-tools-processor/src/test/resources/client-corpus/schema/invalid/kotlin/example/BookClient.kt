package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "broken")
interface BookClient {

    @GraphQLOperation("{ a }")
    fun first(): String

    @GraphQLOperation("{ a }")
    fun second(): String

}
