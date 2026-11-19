package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    String describe(Filter filter) {
        return filter.text
    }

}
