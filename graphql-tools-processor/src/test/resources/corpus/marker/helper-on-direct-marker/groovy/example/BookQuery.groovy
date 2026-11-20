package example

import io.micronaut.graphql.tools.resolver.QueryResolver
import jakarta.inject.Singleton

@Singleton
class BookQuery implements QueryResolver {

    Book book(String id) {
        return new Book()
    }

    String stray(String x) {
        return x
    }

}
