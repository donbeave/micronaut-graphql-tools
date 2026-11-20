package example.books

import jakarta.inject.Singleton
import example.model.*
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class BookQuery {

    Book book(String id) {
        return new Book(id: id, title: 'Dune')
    }

}
