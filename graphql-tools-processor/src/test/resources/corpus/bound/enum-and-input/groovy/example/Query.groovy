package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    Level level() {
        return Level.HIGH
    }

    String describe(Filter filter) {
        return filter.text
    }

}
