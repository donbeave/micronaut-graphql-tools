package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = 'library')
interface BookClient {

    @GraphQLOperation('{ version }')
    String first()

    @GraphQLOperation('{ version }')
    String second()

}
