package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("{ books { ...Titled ...Authored } } fragment Titled on Book { id }")
    fun inline(): Any?

    @GraphQLOperation("Named")
    fun named(): Any?

}
