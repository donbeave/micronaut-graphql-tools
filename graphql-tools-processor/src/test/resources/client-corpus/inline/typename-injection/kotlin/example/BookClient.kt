package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("query Search(\$term: String!) { search(term: \$term) { ... on Book { title } ... on Magazine { issue } } }")
    fun search(term: String): Any?

    @GraphQLOperation("query Hits(\$term: String!) { search(term: \$term) { ...Hit } } fragment Hit on SearchResult { __typename ... on Book { title } }")
    fun hits(term: String): Any?

    @GraphQLOperation("{ books { title } }")
    fun books(): Any?

}
