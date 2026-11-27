package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient
interface BookClient {

    @GraphQLOperation("{ version }")
    fun version(): String

}
