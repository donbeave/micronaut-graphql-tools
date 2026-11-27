package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "server")
interface BookClient {

    @GraphQLOperation("{ version }")
    fun version(): String

    @GraphQLOperation("FromServerDirectory")
    fun named(): String

}
