package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = 'books')
interface BookClient {

    @GraphQLOperation('query Books($genre: Genre, $limit: Int, $ids: [ID!], $first: Int! = 10) { books(genre: $genre, limit: $limit, ids: $ids, first: $first) { title } }')
    Object books(String ids)

}
