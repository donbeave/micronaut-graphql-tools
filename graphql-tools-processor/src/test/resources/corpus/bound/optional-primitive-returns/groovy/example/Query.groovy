package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    OptionalInt count() {
        return OptionalInt.of(3)
    }

    OptionalLong big() {
        return OptionalLong.empty()
    }

    OptionalDouble ratio() {
        return OptionalDouble.of(1.5d)
    }

    List<OptionalInt> counts() {
        return [OptionalInt.of(1), OptionalInt.empty()]
    }

}
