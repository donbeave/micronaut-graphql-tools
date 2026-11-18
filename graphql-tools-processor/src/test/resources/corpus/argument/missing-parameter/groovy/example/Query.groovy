package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    String page(Integer first) {
        return 'first=' + first
    }

}
