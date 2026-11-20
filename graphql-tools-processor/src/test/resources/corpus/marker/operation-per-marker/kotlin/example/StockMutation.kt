package example

import io.micronaut.graphql.tools.resolver.MutationResolver
import jakarta.inject.Singleton

@Singleton
open class StockMutation : MutationResolver {

    open fun stock(): String = "mutated"

}
