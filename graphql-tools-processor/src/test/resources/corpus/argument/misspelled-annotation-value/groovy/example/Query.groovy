package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLArgument
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    String page(@GraphQLArgument('frist') Integer first, Integer offset) {
        return 'first=' + first + ' offset=' + offset
    }

}
