package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class QueryResolver {

    String stray() {
        return ''
    }

    String ping() {
        return 'pong'
    }

}
