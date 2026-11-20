package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    Book book(String id) {
        return null
    }

    List<Book> search(BookFilter filter) {
        return null
    }

}
