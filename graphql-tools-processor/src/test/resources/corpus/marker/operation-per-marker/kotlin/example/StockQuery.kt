package example

import io.micronaut.graphql.tools.resolver.QueryResolver
import jakarta.inject.Singleton

@Singleton
open class StockQuery : QueryResolver {

    open fun stock(): String = "queried"

}
