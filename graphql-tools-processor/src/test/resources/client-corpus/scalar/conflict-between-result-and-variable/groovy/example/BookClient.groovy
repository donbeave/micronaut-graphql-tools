package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = 'books')
interface BookClient {

    @GraphQLOperation('query Stamp { stamp }')
    java.time.Instant stamp()

    @GraphQLOperation(value = 'query Stamp2 { stamp }', name = 'Stamp2')
    String stampText()

}
