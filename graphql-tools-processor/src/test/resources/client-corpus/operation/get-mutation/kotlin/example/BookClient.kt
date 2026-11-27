package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation(value = "mutation Save(\$title: String!) { save(title: \$title) { id } }", method = GraphQLOperation.Method.GET)
    fun save(title: String): Any?

    @GraphQLOperation(value = "subscription Added { bookAdded { id } }", method = GraphQLOperation.Method.GET)
    fun added(): Any?

    @GraphQLOperation(value = "{ version }", method = GraphQLOperation.Method.GET)
    fun version(): Any?

}
