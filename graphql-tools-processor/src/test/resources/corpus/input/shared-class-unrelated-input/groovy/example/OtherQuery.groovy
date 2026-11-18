package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class OtherQuery {

    String other(Extra input) {
        return input.extra
    }

}
