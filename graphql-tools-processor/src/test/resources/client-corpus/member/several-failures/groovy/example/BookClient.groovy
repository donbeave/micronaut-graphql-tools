package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = 'books')
interface BookClient {

    @GraphQLOperation('{ nope }')
    String first()

    @GraphQLOperation('{ version }')
    String fine()

    @GraphQLOperation('Missing')
    String second()

}
