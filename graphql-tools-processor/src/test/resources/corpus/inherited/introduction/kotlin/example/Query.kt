package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@Answering
@GraphQLRootResolver
interface Query {

    fun hello(): String

    fun goodbye(): String

}
