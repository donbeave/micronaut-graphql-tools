package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation
import io.micronaut.graphql.tools.client.annotation.GraphQLVariable
import io.micronaut.http.annotation.QueryValue

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("query Book(\$id: ID!) { book(id: \$id) { title } }")
    fun book(id: String, limit: Int): Any?

    @GraphQLOperation("query Book(\$id: ID!) { book(id: \$id) { title } }")
    fun tagged(id: String, @GraphQLVariable("id") copy: String): Any?

    @GraphQLOperation("query Book(\$id: ID!) { book(id: \$id) { title } }")
    fun queried(id: String, @QueryValue page: String): Any?

}
