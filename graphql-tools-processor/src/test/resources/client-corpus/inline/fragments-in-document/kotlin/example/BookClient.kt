package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation(value = "query Books { books { ...Titled } } fragment Titled on Book { title ...Authored } fragment Authored on Book { author } fragment Unused on Book { id } query Other { version }", name = "Books")
    fun books(): Any?

}
