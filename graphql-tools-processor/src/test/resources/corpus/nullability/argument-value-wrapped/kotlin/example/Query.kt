package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.input.ArgumentValue

@Singleton
@GraphQLRootResolver
class Query {

    fun search(term: ArgumentValue<String>): String? = null

}
