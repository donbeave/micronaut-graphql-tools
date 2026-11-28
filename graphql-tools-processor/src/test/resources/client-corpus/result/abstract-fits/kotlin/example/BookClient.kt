package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("query Search(\$term: String!) { search(term: \$term) { ... on Book { title pages } ... on Magazine { issue } } }")
    fun search(term: String): List<Hit>

    @GraphQLOperation("query Spread(\$term: String!) { search(term: \$term) { ...BookFields ... on Magazine { issue } } } fragment BookFields on Book { title pages }")
    fun spread(term: String): List<Hit>

}
