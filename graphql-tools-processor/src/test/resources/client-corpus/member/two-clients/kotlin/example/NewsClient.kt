package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "news")
interface NewsClient {

    @GraphQLOperation("{ headline }")
    fun headline(): Any?

    @GraphQLOperation("{ version }")
    fun wrong(): Any?

}
