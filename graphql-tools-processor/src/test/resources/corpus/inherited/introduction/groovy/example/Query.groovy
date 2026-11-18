package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Answering
@GraphQLRootResolver
interface Query {

    String hello()

    String goodbye()

}
