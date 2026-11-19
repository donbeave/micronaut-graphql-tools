package example.books

import example.model.*
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class BookQuery {

    Book book(String id) {
        return new Book(id: id, title: 'Dune')
    }

}
