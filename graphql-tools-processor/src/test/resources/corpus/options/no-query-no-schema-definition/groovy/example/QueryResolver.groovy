package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class QueryResolver {

    String ping() {
        return 'pong'
    }

}
