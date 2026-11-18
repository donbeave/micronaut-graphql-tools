package example.graphql.query

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Introduced
@GraphQLRootResolver
interface IntroducedQuery {

    fun introduced(): String
}
