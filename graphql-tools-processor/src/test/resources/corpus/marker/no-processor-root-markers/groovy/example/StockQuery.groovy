package example

import io.micronaut.graphql.tools.resolver.QueryResolver
import jakarta.inject.Singleton

@Singleton
class StockQuery implements QueryResolver {

    String stock() {
        return 'queried'
    }

}
