package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class Query {

    int paint(List<Color> colors) {
        return colors.size()
    }

}
