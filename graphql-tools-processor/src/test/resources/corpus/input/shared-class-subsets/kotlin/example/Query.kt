package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class Query {

    fun create(input: Dto): String = input.name
    fun rename(input: Dto): String = input.name

}
