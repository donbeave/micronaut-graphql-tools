package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("mutation Save(\$input: BookInput!) { save(input: \$input) { id } }")
    fun save(input: BookInput): Any?

}
