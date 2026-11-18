package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
abstract class BaseQuery {

    String hello() {
        return 'base'
    }

}
