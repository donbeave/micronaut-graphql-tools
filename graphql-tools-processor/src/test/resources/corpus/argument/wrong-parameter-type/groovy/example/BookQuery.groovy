package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    Book book(String id) {
        return new Book(title: id)
    }

    List<Book> books(String limit) {
        return []
    }

}
