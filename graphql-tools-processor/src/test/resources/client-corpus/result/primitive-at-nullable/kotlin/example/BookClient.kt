package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("query Rated(\$id: ID!, \$show: Boolean!) { book(id: \$id) { rating pages @include(if: \$show) } }")
    fun book(id: String, show: Boolean): Rated?

}
