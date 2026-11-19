package example.first

import example.model.*
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class FirstQuery {

    Book book(String id) {
        return new Book(id: id, title: 'Dune')
    }

}
