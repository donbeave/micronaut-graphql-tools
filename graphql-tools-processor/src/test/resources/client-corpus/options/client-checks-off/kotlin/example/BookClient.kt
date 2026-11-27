package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("query Q(\$x: Int) { notInTheSchema }")
    fun missing(): String

    @GraphQLOperation("{ alsoNot another }")
    fun two(): String

}
