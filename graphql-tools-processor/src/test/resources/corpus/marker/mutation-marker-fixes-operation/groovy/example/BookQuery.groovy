package example

import io.micronaut.graphql.tools.resolver.QueryResolver
import jakarta.inject.Singleton

@Singleton
class BookQuery implements QueryResolver {

    String book(String id) {
        return id
    }

}
