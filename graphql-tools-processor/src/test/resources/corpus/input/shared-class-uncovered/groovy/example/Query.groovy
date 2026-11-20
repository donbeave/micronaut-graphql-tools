package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    String create(Dto input) {
        return input.name
    }

    String rename(Dto input) {
        return input.name
    }

}
