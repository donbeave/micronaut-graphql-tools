package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation
import io.micronaut.graphql.tools.client.annotation.GraphQLVariable
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Header

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("query Book(\$id: ID!) { book(id: \$id) { title } }")
    fun book(@GraphQLVariable("id") bookId: String, @Header("X-Trace") trace: String, @CookieValue("session") session: String): Any?

}
