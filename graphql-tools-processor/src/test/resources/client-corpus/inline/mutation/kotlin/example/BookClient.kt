package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("mutation Save(\$title: String!) { save(title: \$title) { id } }")
    fun save(title: String): Any?

}
