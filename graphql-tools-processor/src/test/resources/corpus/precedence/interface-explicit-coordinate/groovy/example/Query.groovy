package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    List<Section> sections() {
        return []
    }

}
