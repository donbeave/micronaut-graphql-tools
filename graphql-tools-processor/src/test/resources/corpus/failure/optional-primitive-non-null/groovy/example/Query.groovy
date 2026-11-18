package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    OptionalInt count() {
        return OptionalInt.of(3)
    }

}
