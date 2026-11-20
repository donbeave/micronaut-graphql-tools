package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    Level level() {
        return Level.HIGH
    }

    String describe(Filter filter) {
        return filter.text
    }

}
