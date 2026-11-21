package example

import io.micronaut.graphql.tools.annotation.GraphQLContextValue
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton

@Singleton
@GraphQLRootResolver
class Query {

    String book(String id, @GraphQLContextValue('count') int count) {
        return id
    }

}
