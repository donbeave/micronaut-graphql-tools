package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = 'books')
interface BookClient {

    @GraphQLOperation('query Node($id: ID!) { node(id: $id) { id ... on Book { title } } }')
    Node node(String id)

    @GraphQLOperation('query Plain($id: ID!) { node(id: $id) { id } }')
    Plain plain(String id)

}
