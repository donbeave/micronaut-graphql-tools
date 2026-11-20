package example

import io.micronaut.graphql.tools.resolver.MutationResolver
import jakarta.inject.Singleton

@Singleton
open class BookMutation : MutationResolver {

    open fun ping(): String = "pong"

    open fun book(id: String): String = id

}
