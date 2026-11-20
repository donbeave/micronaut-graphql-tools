package example

import jakarta.inject.Singleton
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    Book book(String id, String locale) {
        return new Book(title: id)
    }

    List<Book> books(Integer limit) {
        return []
    }

}
