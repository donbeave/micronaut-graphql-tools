package example.second

import jakarta.inject.Singleton
import example.model.*
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver

@Singleton
@GraphQLRootResolver
class SecondQuery {

    Book book(String id) {
        return new Book(id: id, title: 'Dune')
    }

}
