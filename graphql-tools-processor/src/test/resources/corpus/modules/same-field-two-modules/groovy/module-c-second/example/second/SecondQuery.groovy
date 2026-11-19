package example.second

import example.model.*
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@GraphQLRootResolver
class SecondQuery {

    Book book(String id) {
        return new Book(id: id, title: 'Dune')
    }

}
