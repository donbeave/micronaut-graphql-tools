package example

import io.micronaut.graphql.tools.resolver.MutationResolver
import jakarta.inject.Singleton

@Singleton
class StockMutation implements MutationResolver {

    String stock() {
        return 'mutated'
    }

}
