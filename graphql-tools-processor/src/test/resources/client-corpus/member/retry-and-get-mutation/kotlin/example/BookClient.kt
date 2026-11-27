package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation
import io.micronaut.retry.annotation.Retryable

@Retryable
@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation(value = "mutation Save(\$title: String!) { save(title: \$title) { id } }", method = GraphQLOperation.Method.GET)
    fun save(title: String): Any?

}
