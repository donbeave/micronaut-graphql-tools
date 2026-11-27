package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = 'books')
interface BookClient {

    @GraphQLOperation('query Search($term: String!) { search(term: $term) { ... on Book { title pages } ... on Magazine { issue } } }')
    java.util.List<Hit> search(String term)

}
