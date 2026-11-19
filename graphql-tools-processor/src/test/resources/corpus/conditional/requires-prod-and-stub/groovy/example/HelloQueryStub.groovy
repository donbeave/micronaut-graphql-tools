package example

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
@Requires(notEnv = 'prod')
class HelloQueryStub {

    String hello() {
        return 'stub'
    }

}
