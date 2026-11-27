package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation
import io.micronaut.retry.annotation.Retryable

@Retryable
@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("{ version }")
    fun version(): Any?

    @GraphQLOperation("mutation Save(\$title: String!) { save(title: \$title) { id } }")
    fun save(title: String): Any?

}
