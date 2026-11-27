package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = 'books')
interface BookClient {

    @GraphQLOperation(value = 'query Book($id: ID!) { book(id: $id) { title } }', method = GraphQLOperation.Method.GET)
    Object book(String id)

    @GraphQLOperation('mutation Save($title: String!) { save(title: $title) { id } }')
    Object save(String title)

}
