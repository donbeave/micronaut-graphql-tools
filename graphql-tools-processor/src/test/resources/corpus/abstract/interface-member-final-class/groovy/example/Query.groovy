package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton

@Singleton
@GraphQLRootResolver
class Query {

    List<Section> sections() {
        return []
    }

}
