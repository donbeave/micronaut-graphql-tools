package example

import io.micronaut.graphql.tools.client.annotation.GraphQLClient
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation

@GraphQLClient(schema = "books")
interface BookClient {

    @GraphQLOperation("query At(\$moment: DateTime) { at(moment: \$moment) }")
    fun at(moment: java.time.Instant?): Any?

    @GraphQLOperation(value = "query At2(\$moment: DateTime) { at(moment: \$moment) }", name = "At2")
    fun atAgain(moment: String): Any?

}
