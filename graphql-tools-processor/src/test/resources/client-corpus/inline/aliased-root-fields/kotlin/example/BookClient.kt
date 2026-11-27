package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("{ v: version }")
    fun one(): String

    @GraphQLOperation("{ a: version b: version }")
    fun two(): String

}
