package example

import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class HelloQuery {

    String hello() {
        return 'hello'
    }

}
