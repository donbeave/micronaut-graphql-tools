package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = 'books')
interface BookClient {

    @GraphQLOperation('query Book($id: ID!) { book(id: $id) { title } }')
    java.util.concurrent.CompletableFuture<io.micronaut.graphql.tools.client.GraphQLResponse<Wrong>> book(String id)

}
